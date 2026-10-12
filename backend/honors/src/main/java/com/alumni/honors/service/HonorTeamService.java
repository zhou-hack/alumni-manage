package com.alumni.honors.service;

import com.alumni.common.pinyin.PinyinUtils;
import com.alumni.common.security.AuthPrincipal;
import com.alumni.honors.common.BizException;
import com.alumni.honors.common.ErrorCode;
import com.alumni.honors.dto.HonorTeamDtos.Candidate;
import com.alumni.honors.dto.HonorTeamDtos.DuplicatedItem;
import com.alumni.honors.dto.HonorTeamDtos.MemberItem;
import com.alumni.honors.dto.HonorTeamDtos.NoAccountItem;
import com.alumni.honors.dto.HonorTeamDtos.TeamCreateRequest;
import com.alumni.honors.dto.HonorTeamDtos.TeamCreateResult;
import com.alumni.honors.dto.HonorTeamDtos.TeamDetailResult;
import com.alumni.honors.dto.HonorTeamDtos.TeamHonorItem;
import com.alumni.honors.dto.HonorTeamDtos.UnmatchedItem;
import com.alumni.honors.entity.Honor;
import com.alumni.honors.entity.HonorTeamMember;
import com.alumni.honors.entity.Review;
import com.alumni.honors.entity.ReviewLog;
import com.alumni.honors.repository.DictItemRepository;
import com.alumni.honors.repository.HonorRepository;
import com.alumni.honors.repository.HonorTeamMemberRepository;
import com.alumni.honors.repository.ReviewLogRepository;
import com.alumni.honors.repository.ReviewRepository;
import com.alumni.honors.service.HonorMatchService.BaseMatch;
import com.alumni.honors.service.HonorMatchService.ExactMatch;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * [M03] 团队荣誉提交编排：导员/管理员上传团队比赛荣誉，
 * 系统解析成员名单 → 逐人名匹配校友库 → 每位识别成功的成员生成一条本人荣誉（共享 team_id）
 * → 未匹配/歧义/无账号人名全部留证（honor_team_members）。
 *
 * <p>起始状态按提交者层级自动定级（导员本人即初审环节，跳过 counselor）：</p>
 * <ul>
 *   <li>counselor → pending_first（Review stage=first）</li>
 *   <li>college_admin → pending_second（Review stage=second）</li>
 *   <li>school_admin / system_admin → approved（无 Review，直接终态）</li>
 * </ul>
 */
@Service
public class HonorTeamService {

    /** 幂等占用状态：同 user+title+date 处于这些状态禁止重复创建（与 HonorService 口径一致） */
    private static final Set<String> IDEMPOTENT_STATUSES =
            Set.of("pending_counselor", "pending_first", "pending_second", "approved");
    /** 可提交团队荣誉的角色 */
    private static final Set<String> SUBMITTER_ROLES =
            Set.of("counselor", "college_admin", "school_admin", "system_admin");
    /** 可查看任意团队详情的角色（导员仅可见本人提交的批次） */
    private static final Set<String> REVIEWER_ROLES =
            Set.of("college_admin", "school_admin", "system_admin");
    private static final String DICT_TYPE_HONOR_CATEGORY = "honor_category";
    private static final String BIZ_TYPE_HONOR = "honor";
    private static final int MEMBER_NAMES_MAX = 1000;
    private static final String OPERATOR_COLLEGE_SQL = """
            SELECT ab.college FROM users u
            JOIN alumni_base ab ON ab.id = u.alumni_base_id
            WHERE u.id = ?
            """;

    private final HonorRepository honorRepository;
    private final HonorTeamMemberRepository honorTeamMemberRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final DictItemRepository dictItemRepository;
    private final HonorMatchService honorMatchService;
    private final JdbcTemplate jdbcTemplate;

    public HonorTeamService(HonorRepository honorRepository,
                            HonorTeamMemberRepository honorTeamMemberRepository,
                            ReviewRepository reviewRepository,
                            ReviewLogRepository reviewLogRepository,
                            DictItemRepository dictItemRepository,
                            HonorMatchService honorMatchService,
                            JdbcTemplate jdbcTemplate) {
        this.honorRepository = honorRepository;
        this.honorTeamMemberRepository = honorTeamMemberRepository;
        this.reviewRepository = reviewRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.dictItemRepository = dictItemRepository;
        this.honorMatchService = honorMatchService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 团队荣誉提交：解析人名 → 匹配 → 逐成员建荣誉（含 Review/日志）→ 全员留证，同一事务 */
    @Transactional
    public TeamCreateResult create(AuthPrincipal operator, TeamCreateRequest request) {
        requireSubmitterRole(operator);
        requireCategoryExists(request.category());

        LinkedHashSet<String> names = honorMatchService.parseNames(request.memberNames());
        if (names.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "member_names 未解析出有效人名（以、，,;；/ 空格分隔）");
        }

        String collegeLabel = resolveOperatorCollege(operator.userId());
        String status = initialStatusByRole(operator.role());
        String stage = switch (status) {
            case "pending_first" -> "first";
            case "pending_second" -> "second";
            default -> null; // approved：直接终态，不生成 Review
        };
        String teamId = UUID.randomUUID().toString();
        String memberNamesText = truncate(request.memberNames().trim(), MEMBER_NAMES_MAX);

        int created = 0;
        List<DuplicatedItem> duplicated = new ArrayList<>();
        List<NoAccountItem> noAccount = new ArrayList<>();
        List<UnmatchedItem> unmatched = new ArrayList<>();

        for (String name : names) {
            List<ExactMatch> matches = honorMatchService.exactMatch(name, collegeLabel);
            if (matches.size() == 1) {
                ExactMatch match = matches.get(0);
                boolean dup = honorRepository.existsByUserIdAndTitleAndAwardedAtAndStatusIn(
                        match.userId(), request.title(), request.awardedAt(), IDEMPOTENT_STATUSES);
                if (dup) {
                    saveMember(teamId, name, match.alumniBaseId(), match.userId(), null,
                            HonorTeamMember.STATUS_DUPLICATED, "同人同题同日荣誉已存在", operator.userId());
                    duplicated.add(new DuplicatedItem(name, match.studentId()));
                } else {
                    Honor honor = createHonorFor(match.userId(), request, teamId, memberNamesText,
                            operator.userId(), status);
                    if (stage != null) {
                        createReview(match.userId(), honor.getId(), stage, operator.userId());
                    }
                    saveMember(teamId, name, match.alumniBaseId(), match.userId(), honor.getId(),
                            HonorTeamMember.STATUS_MATCHED, null, operator.userId());
                    created++;
                }
            } else if (matches.size() > 1) {
                List<Candidate> candidates = honorMatchService.toCandidates(matches);
                saveMember(teamId, name, null, null, null,
                        HonorTeamMember.STATUS_AMBIGUOUS,
                        "同名多人(" + matches.size() + ")：" + candidatesSummary(candidates), operator.userId());
                unmatched.add(new UnmatchedItem(name, "同名多人，需人工确认后重新提交", candidates));
            } else {
                List<BaseMatch> baseMatches = honorMatchService.baseMatches(name);
                if (!baseMatches.isEmpty()) {
                    BaseMatch base = baseMatches.get(0);
                    saveMember(teamId, name, base.alumniBaseId(), null, null,
                            HonorTeamMember.STATUS_NO_ACCOUNT, "姓名命中校友库但账号未激活", operator.userId());
                    noAccount.add(new NoAccountItem(name, base.studentId()));
                } else {
                    List<Candidate> candidates = honorMatchService.pinyinCandidates(name);
                    saveMember(teamId, name, null, null, null,
                            HonorTeamMember.STATUS_UNMATCHED, "校友库无此姓名", operator.userId());
                    unmatched.add(new UnmatchedItem(name, "校友库未命中该姓名", candidates));
                }
            }
        }

        return new TeamCreateResult(teamId, created, duplicated, noAccount, unmatched);
    }

    /**
     * 团队详情：提交者本人或院系/校级/系统管理员可见（导员仅本人批次）；
     * 返回全员留证 + 同批生成荣誉摘要。批次元信息取自首条荣誉（全员未匹配时为 null）。
     */
    @Transactional(readOnly = true)
    public TeamDetailResult detail(AuthPrincipal operator, String teamId) {
        List<HonorTeamMember> members = honorTeamMemberRepository.findByTeamIdOrderByIdAsc(teamId);
        if (members.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "团队荣誉批次不存在");
        }
        Long createdBy = members.get(0).getCreatedBy();
        boolean self = createdBy != null && createdBy.equals(operator.userId());
        boolean reviewer = REVIEWER_ROLES.contains(operator.role());
        if (!self && !reviewer) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅提交人或院系/校级/系统管理员可查看团队详情");
        }

        Map<Long, String> studentIdByUserId = loadStudentIds(
                members.stream().map(HonorTeamMember::getUserId).filter(java.util.Objects::nonNull).toList());

        List<MemberItem> memberItems = members.stream()
                .map(m -> new MemberItem(m.getNameRaw(),
                        m.getUserId() == null ? null : studentIdByUserId.get(m.getUserId()),
                        m.getAlumniBaseId(), m.getUserId(), m.getHonorId(), m.getMatchStatus(), m.getReason()))
                .toList();

        List<Honor> honors = honorRepository.findByTeamIdOrderByIdAsc(teamId);
        List<TeamHonorItem> honorItems = honors.stream()
                .map(h -> new TeamHonorItem(h.getId(), h.getUserId(), h.getTitle(), h.getStatus()))
                .toList();

        Honor first = honors.isEmpty() ? null : honors.get(0);
        return new TeamDetailResult(teamId,
                first == null ? null : first.getTitle(),
                first == null ? null : first.getCategory(),
                first == null ? null : first.getLevel(),
                first == null ? null : first.getAwardedAt(),
                first == null ? null : first.getRedDocUrl(),
                createdBy, memberItems, honorItems);
    }

    // ------------------------------------------------------------------
    // 内部规则
    // ------------------------------------------------------------------

    private void requireSubmitterRole(AuthPrincipal operator) {
        if (operator == null || !SUBMITTER_ROLES.contains(operator.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅导员/院系/校级/系统管理员可提交团队荣誉");
        }
    }

    private void requireCategoryExists(String category) {
        if (!dictItemRepository.existsByTypeAndValue(DICT_TYPE_HONOR_CATEGORY, category)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "荣誉类别不存在");
        }
    }

    /** 提交者层级 → 起始状态：导员即初审环节故跳过 counselor；校级/系统管理员直接终态 */
    private String initialStatusByRole(String role) {
        return switch (role) {
            case "counselor" -> "pending_first";
            case "college_admin" -> "pending_second";
            default -> "approved"; // school_admin / system_admin
        };
    }

    /** 提交者本院文本（users → alumni_base.college；桩行缺失时返回 null 走全校匹配） */
    private String resolveOperatorCollege(Long userId) {
        try {
            return jdbcTemplate.queryForObject(OPERATOR_COLLEGE_SQL, String.class, userId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** 为识别成功的成员创建本人荣誉（含标题拼音列） */
    private Honor createHonorFor(Long memberUserId, TeamCreateRequest request, String teamId,
                                  String memberNames, Long createdBy, String status) {
        Honor honor = new Honor();
        honor.setUserId(memberUserId);
        honor.setTeamId(teamId);
        honor.setMemberNames(memberNames);
        honor.setCreatedBy(createdBy);
        honor.setRedDocUrl(request.redDocUrl());
        honor.setTitle(request.title());
        honor.setTitlePinyin(PinyinUtils.toFullPinyin(request.title()));
        honor.setTitlePinyinAbbr(PinyinUtils.toAbbr(request.title()));
        honor.setCategory(request.category());
        honor.setLevel(request.level());
        honor.setAwardedAt(request.awardedAt());
        honor.setStatus(status);
        return honorRepository.saveAndFlush(honor);
    }

    /** 生成待审记录（biz_type=honor；submitter=成员本人，审核结果通知发给成员） */
    private void createReview(Long memberUserId, Long honorId, String stage, Long operatorId) {
        Review review = new Review();
        review.setBizType(BIZ_TYPE_HONOR);
        review.setBizId(honorId);
        review.setSubmitterId(memberUserId);
        review.setStage(stage);
        review.setStatus("pending");
        review = reviewRepository.save(review);

        ReviewLog log = new ReviewLog();
        log.setReviewId(review.getId());
        log.setOperatorId(operatorId);
        log.setAction(ReviewLog.ACTION_CREATE);
        reviewLogRepository.save(log);
    }

    /** 成员留证行（全部人名入表，含未匹配） */
    private void saveMember(String teamId, String name, Long alumniBaseId, Long userId, Long honorId,
                            String matchStatus, String reason, Long createdBy) {
        HonorTeamMember member = new HonorTeamMember();
        member.setTeamId(teamId);
        member.setNameRaw(name);
        member.setAlumniBaseId(alumniBaseId);
        member.setUserId(userId);
        member.setHonorId(honorId);
        member.setMatchStatus(matchStatus);
        member.setReason(reason);
        member.setCreatedBy(createdBy);
        honorTeamMemberRepository.save(member);
    }

    /** 批量取 userId → student_id（详情出参，一次查询避免 N+1） */
    private Map<Long, String> loadStudentIds(List<Long> userIds) {
        Map<Long, String> map = new LinkedHashMap<>();
        if (userIds.isEmpty()) {
            return map;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(userIds.size(), "?"));
        jdbcTemplate.query("SELECT id, student_id FROM users WHERE id IN (" + placeholders + ")",
                rs -> {
                    map.put(rs.getLong("id"), rs.getString("student_id"));
                }, userIds.toArray());
        return map;
    }

    /** 候选摘要：学号@学院，逗号分隔（写入留证 reason） */
    private String candidatesSummary(List<Candidate> candidates) {
        StringBuilder sb = new StringBuilder();
        for (Candidate c : candidates) {
            if (sb.length() > 0) {
                sb.append("，");
            }
            sb.append(c.studentId()).append("@").append(c.college());
        }
        return sb.toString();
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
