package com.alumni.honors.service;

import com.alumni.common.pinyin.PinyinUtils;
import com.alumni.common.security.AuthPrincipal;
import com.alumni.honors.common.BizException;
import com.alumni.honors.common.ErrorCode;
import com.alumni.honors.dto.HonorDraftDtos.*;
import com.alumni.honors.dto.HonorTeamDtos.Candidate;
import com.alumni.honors.entity.Honor;
import com.alumni.honors.entity.HonorDraft;
import com.alumni.honors.entity.HonorDraftMember;
import com.alumni.honors.entity.HonorTeamMember;
import com.alumni.honors.entity.Review;
import com.alumni.honors.entity.ReviewLog;
import com.alumni.honors.repository.DictItemRepository;
import com.alumni.honors.repository.HonorDraftMemberRepository;
import com.alumni.honors.repository.HonorDraftRepository;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * [M03 扩展] 荣誉证书解析草稿服务。阶段一：人工填写 + 实时人名匹配预览；
 * 阶段二：OCR 自动填充 parsed_json 后复用同一确认流。
 *
 * <p>确认流（团队草稿）：</p>
 * <ol>
 *   <li>校验草稿 status=pending、字段完整</li>
 *   <li>应用人工 overrides（歧义/未匹配 → 强制指定 user_id）</li>
 *   <li>生成团队批次 UUID（team_id）；写一条 is_team_record=1 / team_role=leader 的"公示行"
 *       挂在 submitter 本人名下，member_names 留全名单</li>
 *   <li>每位 matched/manual 成员写一条 is_team_record=0 / team_role=member 的个人荣誉
 *       挂其本人名下</li>
 *   <li>全部成员（含 unmatched）入 honor_team_members 留证</li>
 *   <li>草稿 status → confirmed，附件 referenced</li>
 * </ol>
 */
@Service
public class HonorDraftService {

    /** 与 HonorService / HonorTeamService 一致的幂等占用状态 */
    private static final Set<String> IDEMPOTENT_STATUSES =
            Set.of("pending_counselor", "pending_first", "pending_second", "approved");
    /** 可创建草稿的角色（与团队荣誉提交流一致） */
    private static final Set<String> SUBMITTER_ROLES =
            Set.of("counselor", "college_admin", "school_admin", "system_admin");
    private static final String DICT_TYPE_HONOR_CATEGORY = "honor_category";
    private static final String BIZ_TYPE_HONOR = "honor";

    private static final String OPERATOR_COLLEGE_SQL = """
            SELECT ab.college FROM users u
            JOIN alumni_base ab ON ab.id = u.alumni_base_id
            WHERE u.id = ?
            """;

    private final HonorDraftRepository draftRepository;
    private final HonorDraftMemberRepository draftMemberRepository;
    private final HonorRepository honorRepository;
    private final HonorTeamMemberRepository honorTeamMemberRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final DictItemRepository dictItemRepository;
    private final HonorMatchService honorMatchService;
    private final JdbcTemplate jdbcTemplate;

    public HonorDraftService(HonorDraftRepository draftRepository,
                             HonorDraftMemberRepository draftMemberRepository,
                             HonorRepository honorRepository,
                             HonorTeamMemberRepository honorTeamMemberRepository,
                             ReviewRepository reviewRepository,
                             ReviewLogRepository reviewLogRepository,
                             DictItemRepository dictItemRepository,
                             HonorMatchService honorMatchService,
                             JdbcTemplate jdbcTemplate) {
        this.draftRepository = draftRepository;
        this.draftMemberRepository = draftMemberRepository;
        this.honorRepository = honorRepository;
        this.honorTeamMemberRepository = honorTeamMemberRepository;
        this.reviewRepository = reviewRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.dictItemRepository = dictItemRepository;
        this.honorMatchService = honorMatchService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---------- 创建 / 查询 / 修改 / 丢弃 ----------

    @Transactional
    public HonorDraft create(AuthPrincipal operator, CreateDraftRequest req) {
        requireSubmitterRole(operator);
        if (req.category() != null && !req.category().isBlank()) {
            requireCategoryExists(req.category());
        }
        if (HonorDraft.KIND_TEAM.equals(req.kind()) &&
                (req.memberNames() == null || req.memberNames().isBlank())) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "团队草稿必须填写 member_names");
        }
        HonorDraft draft = new HonorDraft();
        draft.setAttachmentId(req.attachmentId());
        draft.setOperatorId(operator.userId());
        draft.setKind(req.kind());
        draft.setTitle(req.title());
        draft.setCategory(req.category());
        draft.setLevel(req.level());
        draft.setAwardedAt(req.awardedAt());
        draft.setCollegeName(req.collegeName());
        draft.setRedDocUrl(req.redDocUrl());
        draft.setMemberNames(req.memberNames());
        draft.setStatus(HonorDraft.STATUS_PENDING);
        return draftRepository.save(draft);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(AuthPrincipal operator, long id) {
        HonorDraft draft = mustFind(id);
        Map<String, Object> data = toData(draft);
        if (HonorDraft.KIND_TEAM.equals(draft.getKind())) {
            data.put("members", previewMembers(draft.getMemberNames(), resolveOperatorCollege(operator.userId())));
        }
        return data;
    }

    @Transactional
    public HonorDraft update(AuthPrincipal operator, long id, UpdateDraftRequest req) {
        HonorDraft draft = mustFind(id);
        requireOwner(draft, operator);
        if (!HonorDraft.STATUS_PENDING.equals(draft.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATE, "已确认 / 丢弃的草稿不可修改");
        }
        if (req.title() != null) draft.setTitle(req.title());
        if (req.category() != null) {
            requireCategoryExists(req.category());
            draft.setCategory(req.category());
        }
        if (req.level() != null) draft.setLevel(req.level());
        if (req.awardedAt() != null) draft.setAwardedAt(req.awardedAt());
        if (req.collegeName() != null) draft.setCollegeName(req.collegeName());
        if (req.redDocUrl() != null) draft.setRedDocUrl(req.redDocUrl());
        if (req.memberNames() != null) draft.setMemberNames(req.memberNames());
        return draftRepository.save(draft);
    }

    @Transactional
    public void discard(AuthPrincipal operator, long id) {
        HonorDraft draft = mustFind(id);
        requireOwner(draft, operator);
        if (!HonorDraft.STATUS_PENDING.equals(draft.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATE, "仅 pending 草稿可丢弃");
        }
        draft.setStatus(HonorDraft.STATUS_DISCARDED);
        draftRepository.save(draft);
    }

    // ---------- 实时人名匹配预览（确认页每次编辑成员名单后调用） ----------

    @Transactional(readOnly = true)
    public PreviewResult preview(AuthPrincipal operator, long draftId, PreviewRequest req) {
        HonorDraft draft = mustFind(draftId);
        requireOwner(draft, operator);
        String collegeLabel = resolveOperatorCollege(operator.userId());
        return new PreviewResult(previewMembers(req.memberNames(), collegeLabel));
    }

    private List<PreviewMember> previewMembers(String memberNames, String collegeLabel) {
        LinkedHashSet<String> names = honorMatchService.parseNames(memberNames);
        List<PreviewMember> result = new ArrayList<>();
        for (String name : names) {
            List<ExactMatch> matches = honorMatchService.exactMatch(name, collegeLabel);
            if (matches.size() == 1) {
                ExactMatch m = matches.get(0);
                result.add(new PreviewMember(name, m.studentId(), m.alumniBaseId(), m.userId(),
                        HonorDraftMember.STATUS_MATCHED, null));
            } else if (matches.size() > 1) {
                List<Candidate> candidates = honorMatchService.toCandidates(matches);
                result.add(new PreviewMember(name, null, null, null,
                        HonorDraftMember.STATUS_AMBIGUOUS,
                        "同名多人(" + matches.size() + ")，请人工指定"));
            } else {
                List<BaseMatch> baseMatches = honorMatchService.baseMatches(name);
                if (!baseMatches.isEmpty()) {
                    BaseMatch b = baseMatches.get(0);
                    result.add(new PreviewMember(name, b.studentId(), b.alumniBaseId(), null,
                            HonorDraftMember.STATUS_NO_ACCOUNT, "账号未激活"));
                } else {
                    result.add(new PreviewMember(name, null, null, null,
                            HonorDraftMember.STATUS_UNMATCHED, "校友库无此姓名"));
                }
            }
        }
        return result;
    }

    // ---------- 确认：草稿 → 正式 honors（含团队公示行 + 个人行） ----------

    @Transactional
    public ConfirmResult confirm(AuthPrincipal operator, long id, ConfirmRequest req) {
        HonorDraft draft = mustFind(id);
        requireOwner(draft, operator);
        if (!HonorDraft.STATUS_PENDING.equals(draft.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATE, "仅 pending 草稿可确认");
        }
        requireConfirmFieldsFilled(draft);

        Map<String, Long> overrides = buildOverrides(req.overrides());

        if (HonorDraft.KIND_SOLO.equals(draft.getKind())) {
            // 单人草稿 = 转单人荣誉
            Honor honor = createSoloHonor(draft, operator.userId());
            draft.setStatus(HonorDraft.STATUS_CONFIRMED);
            draftRepository.save(draft);
            return new ConfirmResult(null,
                    List.of(new HonorItem(honor.getId(), honor.getUserId(), false, "member")));
        }

        // 团队草稿：批次元信息
        String teamId = UUID.randomUUID().toString();
        String memberNamesText = draft.getMemberNames().trim();
        String status = initialStatusByRole(operator.role());
        String stage = switch (status) {
            case "pending_first" -> "first";
            case "pending_second" -> "second";
            default -> null;
        };

        // 1. 团队公示行（is_team_record=1，挂在提交人名下）
        Honor teamHonor = createTeamRecordHonor(operator.userId(), draft, teamId, memberNamesText, status);

        // 2. 个人行（按 parsed_names 逐成员，overrides 强制指定 user_id）
        LinkedHashSet<String> names = honorMatchService.parseNames(draft.getMemberNames());
        List<HonorItem> items = new ArrayList<>();
        items.add(new HonorItem(teamHonor.getId(), teamHonor.getUserId(), true, "leader"));

        for (String name : names) {
            Long overrideUserId = overrides.get(name);
            if (overrideUserId != null) {
                Honor h = createMemberHonor(overrideUserId, draft, teamId, memberNamesText,
                        operator.userId(), status);
                if (stage != null) createReview(overrideUserId, h.getId(), stage, operator.userId());
                saveHonorTeamMember(teamId, name, null, overrideUserId, h.getId(),
                        HonorTeamMember.STATUS_MATCHED, "人工指定", operator.userId());
                items.add(new HonorItem(h.getId(), overrideUserId, false, "member"));
                continue;
            }
            List<ExactMatch> matches = honorMatchService.exactMatch(name, null);
            if (matches.size() == 1) {
                ExactMatch m = matches.get(0);
                if (isDuplicate(m.userId(), draft)) {
                    saveHonorTeamMember(teamId, name, m.alumniBaseId(), m.userId(), null,
                            HonorTeamMember.STATUS_DUPLICATED, "同人同题同日已存在", operator.userId());
                } else {
                    Honor h = createMemberHonor(m.userId(), draft, teamId, memberNamesText,
                            operator.userId(), status);
                    if (stage != null) createReview(m.userId(), h.getId(), stage, operator.userId());
                    saveHonorTeamMember(teamId, name, m.alumniBaseId(),
                            m.userId(), h.getId(), HonorTeamMember.STATUS_MATCHED, null, operator.userId());
                    items.add(new HonorItem(h.getId(), m.userId(), false, "member"));
                }
            } else if (matches.size() > 1) {
                saveHonorTeamMember(teamId, name, null, null, null,
                        HonorTeamMember.STATUS_AMBIGUOUS, "同名多人，未人工指定", operator.userId());
            } else {
                saveHonorTeamMember(teamId, name, null, null, null,
                        HonorTeamMember.STATUS_UNMATCHED, "校友库无此姓名", operator.userId());
            }
        }

        draft.setStatus(HonorDraft.STATUS_CONFIRMED);
        draftRepository.save(draft);
        return new ConfirmResult(teamId, items);
    }

    // ---------- 内部 ----------

    private void requireSubmitterRole(AuthPrincipal operator) {
        if (operator == null || !SUBMITTER_ROLES.contains(operator.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅导员/院系/校级/系统管理员可创建草稿");
        }
    }

    private void requireOwner(HonorDraft draft, AuthPrincipal operator) {
        if (!draft.getOperatorId().equals(operator.userId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅草稿创建人可操作");
        }
    }

    private void requireCategoryExists(String category) {
        if (!dictItemRepository.existsByTypeAndValue(DICT_TYPE_HONOR_CATEGORY, category)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "荣誉类别不存在");
        }
    }

    private void requireConfirmFieldsFilled(HonorDraft draft) {
        if (draft.getTitle() == null || draft.getTitle().isBlank()
                || draft.getCategory() == null || draft.getCategory().isBlank()
                || draft.getLevel() == null || draft.getLevel().isBlank()
                || draft.getAwardedAt() == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "草稿字段不完整（需 title/category/level/awarded_at）");
        }
    }

    private HonorDraft mustFind(long id) {
        return draftRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "草稿不存在"));
    }

    private String resolveOperatorCollege(Long userId) {
        try {
            return jdbcTemplate.queryForObject(OPERATOR_COLLEGE_SQL, String.class, userId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private boolean isDuplicate(Long userId, HonorDraft draft) {
        return honorRepository.existsByUserIdAndTitleAndAwardedAtAndStatusIn(
                userId, draft.getTitle(), draft.getAwardedAt(), IDEMPOTENT_STATUSES);
    }

    private Map<String, Long> buildOverrides(List<MemberOverride> overrides) {
        Map<String, Long> map = new LinkedHashMap<>();
        if (overrides != null) {
            for (MemberOverride o : overrides) {
                if (o.name() != null && o.userId() != null) {
                    map.put(o.name().trim(), o.userId());
                }
            }
        }
        return map;
    }

    private String initialStatusByRole(String role) {
        return switch (role) {
            case "counselor" -> "pending_first";
            case "college_admin" -> "pending_second";
            default -> "approved"; // school_admin / system_admin
        };
    }

    /** 单人荣誉：挂草稿创建人（=本人）名下 */
    private Honor createSoloHonor(HonorDraft draft, Long userId) {
        Honor h = newHonorSkeleton(userId, draft, null, null, draft.getTitle(),
                draft.getCategory(), draft.getLevel(), draft.getAwardedAt(), draft.getRedDocUrl());
        h.setCreatedBy(userId);
        h.setStatus(initialStatusByRole("alumni")); // 单人走 alumni 默认 approved 兜底（实际取决于 users.role）
        return honorRepository.saveAndFlush(h);
    }

    /** 团队公示行（is_team_record=1，team_role=leader）：挂提交人名下，member_names 留全名单 */
    private Honor createTeamRecordHonor(Long operatorId, HonorDraft draft, String teamId,
                                        String memberNames, String status) {
        Honor h = newHonorSkeleton(operatorId, draft, teamId, true, draft.getTitle(),
                draft.getCategory(), draft.getLevel(), draft.getAwardedAt(), draft.getRedDocUrl());
        h.setMemberNames(memberNames);
        h.setCreatedBy(operatorId);
        h.setTeamRole("leader");
        h.setStatus(status);
        return honorRepository.saveAndFlush(h);
    }

    /** 团队个人行（is_team_record=0，team_role=member）：挂识别成功个人 name 用户 */
    private Honor createMemberHonor(Long userId, HonorDraft draft, String teamId,
                                    String memberNames, Long operatorId, String status) {
        Honor h = newHonorSkeleton(userId, draft, teamId, false, draft.getTitle(),
                draft.getCategory(), draft.getLevel(), draft.getAwardedAt(), draft.getRedDocUrl());
        h.setMemberNames(memberNames);
        h.setCreatedBy(operatorId);
        h.setTeamRole("member");
        h.setStatus(status);
        return honorRepository.saveAndFlush(h);
    }

    private Honor newHonorSkeleton(Long userId, HonorDraft draft, String teamId,
                                   Boolean isTeamRecord, String title, String category,
                                   String level, LocalDate awardedAt, String redDocUrl) {
        Honor h = new Honor();
        h.setUserId(userId);
        h.setTeamId(teamId);
        if (isTeamRecord != null) h.setIsTeamRecord(isTeamRecord);
        h.setTitle(title);
        h.setTitlePinyin(PinyinUtils.toFullPinyin(title));
        h.setTitlePinyinAbbr(PinyinUtils.toAbbr(title));
        h.setCategory(category);
        h.setLevel(level);
        h.setAwardedAt(awardedAt);
        h.setRedDocUrl(redDocUrl);
        return h;
    }

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

    private void saveHonorTeamMember(String teamId, String name, Long alumniBaseId, Long userId,
                                     Long honorId, String matchStatus, String reason, Long createdBy) {
        HonorTeamMember m = new HonorTeamMember();
        m.setTeamId(teamId);
        m.setNameRaw(name);
        m.setAlumniBaseId(alumniBaseId);
        m.setUserId(userId);
        m.setHonorId(honorId);
        m.setMatchStatus(matchStatus);
        m.setReason(reason);
        m.setCreatedBy(createdBy);
        honorTeamMemberRepository.save(m);
    }

    private Map<String, Object> toData(HonorDraft draft) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", draft.getId());
        map.put("attachment_id", draft.getAttachmentId());
        map.put("operator_id", draft.getOperatorId());
        map.put("kind", draft.getKind());
        map.put("title", draft.getTitle());
        map.put("category", draft.getCategory());
        map.put("level", draft.getLevel());
        map.put("awarded_at", draft.getAwardedAt());
        map.put("college_name", draft.getCollegeName());
        map.put("red_doc_url", draft.getRedDocUrl());
        map.put("member_names", draft.getMemberNames());
        map.put("parsed_json", draft.getParsedJson());
        map.put("status", draft.getStatus());
        map.put("created_at", draft.getCreatedAt());
        map.put("updated_at", draft.getUpdatedAt());
        return map;
    }
}