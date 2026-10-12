package com.alumni.honors.service;

import com.alumni.common.pinyin.PinyinUtils;
import com.alumni.common.security.AuthPrincipal;
import com.alumni.honors.common.BizException;
import com.alumni.honors.common.ErrorCode;
import com.alumni.honors.common.ExportRateLimiter;
import com.alumni.honors.dto.HonorAdminDtos.AdminHonorItem;
import com.alumni.honors.dto.HonorAdminDtos.ImportErrorItem;
import com.alumni.honors.dto.HonorAdminDtos.ImportResult;
import com.alumni.honors.dto.HonorAdminDtos.PageResult;
import com.alumni.honors.entity.Honor;
import com.alumni.honors.entity.Review;
import com.alumni.honors.entity.ReviewLog;
import com.alumni.honors.repository.DictItemRepository;
import com.alumni.honors.repository.HonorRepository;
import com.alumni.honors.repository.ReviewLogRepository;
import com.alumni.honors.repository.ReviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * [M03] 荣誉管理端：分级列表（学院隔离）、xlsx 导入导出、模板生成。
 *
 * <p>分级管理：college_admin 仅可见本院荣誉（users→alumni_base→college 对齐 dicts 文本），
 * school_admin / system_admin 全校；URL 级角色限制见 common SecurityConfig，本层二次校验。</p>
 *
 * <p>导入定级（用户已确认决策）：college_admin 导入 → pending_second（校级复核）；
 * school_admin / system_admin 导入 → approved（官方数据免审）；导员不可导入（403）。</p>
 */
@Service
public class HonorAdminService {

    /** 幂等占用状态（与 HonorService 口径一致） */
    private static final Set<String> IDEMPOTENT_STATUSES =
            Set.of("pending_counselor", "pending_first", "pending_second", "approved");
    /** 合法荣誉状态（筛选校验用） */
    private static final Set<String> HONOR_STATUSES =
            Set.of("pending_counselor", "pending_first", "pending_second", "approved", "rejected");
    /** 合法大屏上下架状态（筛选校验用） */
    private static final Set<String> SCREEN_STATUSES = Set.of("online", "offline");
    /** 可访问管理端的角色（导员 403） */
    private static final Set<String> ADMIN_ROLES = Set.of("college_admin", "school_admin", "system_admin");
    private static final String DICT_TYPE_HONOR_CATEGORY = "honor_category";
    private static final String DICT_TYPE_COLLEGE = "college";
    private static final String BIZ_TYPE_HONOR = "honor";
    public static final int MAX_PAGE_SIZE = 100;
    /** 单次导入行数上限（同步处理，量级轻） */
    public static final int IMPORT_MAX_ROWS = 500;

    private static final String LIST_FROM = """
            FROM honors h
            JOIN users u ON u.id = h.user_id
            JOIN alumni_base ab ON ab.id = u.alumni_base_id
            LEFT JOIN users cu ON cu.id = h.created_by
            LEFT JOIN alumni_base cab ON cab.id = cu.alumni_base_id
            """;
    private static final String LIST_SELECT = """
            SELECT h.id, h.user_id, ab.student_id, ab.real_name, ab.college, ab.grad_year,
                   h.team_id, h.title, h.category, h.level, h.awarded_at, h.status,
                   h.screen_status, h.red_doc_url, h.created_by, cab.real_name AS created_by_name, h.created_at
            """ + LIST_FROM;
    private static final String OPERATOR_COLLEGE_SQL = """
            SELECT ab.college FROM users u
            JOIN alumni_base ab ON ab.id = u.alumni_base_id
            WHERE u.id = ?
            """;
    private static final String USER_BY_STUDENT_SQL = """
            SELECT u.id, u.status FROM users u
            JOIN alumni_base ab ON ab.id = u.alumni_base_id
            WHERE ab.student_id = ? AND u.role IN ('student', 'alumni')
            """;

    private static final String[] EXPORT_HEADERS = {"荣誉ID", "学号", "姓名", "学院", "届别", "团队批次",
            "荣誉标题", "类别", "级别", "授予日期", "状态", "红头文件链接", "提交人", "创建时间"};
    private static final String[] IMPORT_HEADERS = {"student_id", "title", "category", "level",
            "awarded_at", "red_doc_url"};

    private final HonorRepository honorRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final DictItemRepository dictItemRepository;
    private final ExportRateLimiter exportRateLimiter;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HonorAdminService(HonorRepository honorRepository,
                             ReviewRepository reviewRepository,
                             ReviewLogRepository reviewLogRepository,
                             DictItemRepository dictItemRepository,
                             ExportRateLimiter exportRateLimiter,
                             JdbcTemplate jdbcTemplate) {
        this.honorRepository = honorRepository;
        this.reviewRepository = reviewRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.dictItemRepository = dictItemRepository;
        this.exportRateLimiter = exportRateLimiter;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ------------------------------------------------------------------
    // 管理列表（学院隔离 + 多条件筛选）
    // ------------------------------------------------------------------

    /**
     * 荣誉管理列表：college_admin 强制本院（数据隔离红线）；school/system_admin 全校可选 college_id 筛选。
     * keyword 检索姓名/学号/标题（含拼音容错）。
     */
    @Transactional(readOnly = true)
    public PageResult list(AuthPrincipal operator, String keyword, String studentId, Long collegeId,
                           String category, String level, String status, String screenStatus,
                           Integer year, Boolean hasRedDoc, int page, int pageSize) {
        requireAdminRole(operator);
        if (page < 1) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "page 必须从 1 开始");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "page_size 必须在 1~" + MAX_PAGE_SIZE + "之间");
        }
        if (status != null && !status.isBlank() && !HONOR_STATUSES.contains(status)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "status 取值不合法");
        }
        if (screenStatus != null && !screenStatus.isBlank() && !SCREEN_STATUSES.contains(screenStatus)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "screen_status 取值不合法（仅 online/offline）");
        }

        // college_admin 数据隔离：强制本院文本（无本院信息时返回空列表）
        String forcedCollege = "college_admin".equals(operator.role())
                ? resolveOperatorCollege(operator.userId()) : null;
        String collegeLabel = resolveCollegeLabel(collegeId);
        if ("college_admin".equals(operator.role())) {
            if (forcedCollege == null) {
                return new PageResult(List.of(), 0, page, pageSize);
            }
            collegeLabel = forcedCollege; // 院管 college_id 筛选取交集：仅本院生效
        }

        ListParams params = buildWhere(keyword, studentId, collegeLabel, category, level,
                status, screenStatus, year, hasRedDoc);

        String dataSql = LIST_SELECT + params.where() + " ORDER BY h.created_at DESC, h.id DESC LIMIT ? OFFSET ?";
        List<Object> dataArgs = new ArrayList<>(params.args());
        dataArgs.add(pageSize);
        dataArgs.add((long) (page - 1) * pageSize);
        List<AdminHonorItem> list = jdbcTemplate.query(dataSql, this::mapItem, dataArgs.toArray());

        String countSql = "SELECT COUNT(*) " + LIST_FROM + params.where();
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.args().toArray());

        return new PageResult(list, total == null ? 0 : total, page, pageSize);
    }

    // ------------------------------------------------------------------
    // 导出 xlsx（限流 + 自审计）
    // ------------------------------------------------------------------

    /** 导出：接受与列表相同筛选；单账号 5 次/小时；导出行为写 audit_logs（action=export） */
    @Transactional
    public byte[] export(AuthPrincipal operator, String keyword, String studentId, Long collegeId,
                         String category, String level, String status, String screenStatus,
                         Integer year, Boolean hasRedDoc, String ip) {
        requireAdminRole(operator);
        if (!exportRateLimiter.tryAcquire(operator.userId())) {
            throw new BizException(ErrorCode.EXPORT_LIMIT_EXCEEDED, "导出过于频繁：单账号每小时最多 5 次");
        }
        if (status != null && !status.isBlank() && !HONOR_STATUSES.contains(status)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "status 取值不合法");
        }
        if (screenStatus != null && !screenStatus.isBlank() && !SCREEN_STATUSES.contains(screenStatus)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "screen_status 取值不合法（仅 online/offline）");
        }
        String forcedCollege = "college_admin".equals(operator.role())
                ? resolveOperatorCollege(operator.userId()) : null;
        if (forcedCollege == null && "college_admin".equals(operator.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前账号无本院信息，禁止导出（防越权全量导出）");
        }
        String collegeLabel = resolveCollegeLabel(collegeId);
        if (forcedCollege != null) {
            collegeLabel = forcedCollege;
        }

        ListParams params = buildWhere(keyword, studentId, collegeLabel, category, level,
                status, screenStatus, year, hasRedDoc);
        String sql = LIST_SELECT + params.where() + " ORDER BY h.created_at DESC, h.id DESC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.args().toArray());

        byte[] bytes = toXlsx(rows);
        writeExportAudit(operator, rows.size(), keyword, status, ip);
        return bytes;
    }

    private byte[] toXlsx(List<Map<String, Object>> rows) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("honors");
            Row header = sheet.createRow(0);
            for (int i = 0; i < EXPORT_HEADERS.length; i++) {
                header.createCell(i).setCellValue(EXPORT_HEADERS[i]);
            }
            int rowIndex = 1;
            for (Map<String, Object> row : rows) {
                Row r = sheet.createRow(rowIndex++);
                for (int i = 0; i < EXPORT_HEADERS.length; i++) {
                    String value = cellText(row, i);
                    if (!value.isEmpty()) {
                        r.createCell(i).setCellValue(value);
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("生成导出文件失败", e);
        }
    }

    /** 行数据 → 导出列文本（列序与 EXPORT_HEADERS 一一对应） */
    private String cellText(Map<String, Object> row, int col) {
        Object v = switch (col) {
            case 0 -> row.get("id");
            case 1 -> row.get("student_id");
            case 2 -> row.get("real_name");
            case 3 -> row.get("college");
            case 4 -> row.get("grad_year");
            case 5 -> row.get("team_id");
            case 6 -> row.get("title");
            case 7 -> row.get("category");
            case 8 -> row.get("level");
            case 9 -> row.get("awarded_at");
            case 10 -> row.get("status");
            case 11 -> row.get("red_doc_url");
            case 12 -> row.get("created_by_name");
            case 13 -> row.get("created_at");
            default -> null;
        };
        return v == null ? "" : String.valueOf(v);
    }

    /** 导出行为自审计（只追加一条 audit_logs，随导出事务提交） */
    private void writeExportAudit(AuthPrincipal operator, int rows, String keyword, String status, String ip) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("rows", rows);
        detail.put("keyword", keyword);
        detail.put("status", status);
        String detailJson;
        try {
            detailJson = objectMapper.writeValueAsString(detail);
        } catch (IOException e) {
            detailJson = "{\"rows\":" + rows + "}";
        }
        jdbcTemplate.update("""
                INSERT INTO audit_logs (user_id, user_role, action, biz_type, biz_id, detail, ip, created_at)
                VALUES (?, ?, 'export', 'honor', NULL, ?, ?, UTC_TIMESTAMP(6))
                """, operator.userId(), operator.role(), detailJson, ip);
    }

    // ------------------------------------------------------------------
    // 批量导入（同步 ≤500 行，坏行不阻断好行）
    // ------------------------------------------------------------------

    /** GET /admin/honors/import/template：生成导入模板（数据 sheet + 填写说明 sheet） */
    public byte[] importTemplate() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet data = wb.createSheet("导入数据");
            Row header = data.createRow(0);
            for (int i = 0; i < IMPORT_HEADERS.length; i++) {
                header.createCell(i).setCellValue(IMPORT_HEADERS[i]);
                data.setColumnWidth(i, 18 * 256);
            }
            Row sample = data.createRow(1);
            String[] sampleValues = {"2021000101", "全国大学生程序设计竞赛金奖", "competition",
                    "national", "2026-05-20", "https://example.edu.cn/doc/2026-001.pdf"};
            for (int i = 0; i < sampleValues.length; i++) {
                sample.createCell(i).setCellValue(sampleValues[i]);
            }

            Sheet guide = wb.createSheet("填写说明");
            String[][] rules = {
                    {"列", "必填", "规则"},
                    {"student_id", "是", "学号（须已存在于校友库且账号已激活的在校生/校友）"},
                    {"title", "是", "荣誉标题，最长 128 字符；同人同题同日重复行记为错误行"},
                    {"category", "是", "荣誉类别编码，须在 honor_category 字典内（如 scholarship/competition）"},
                    {"level", "是", "级别，最长 32 字符（如 national/provincial/school）"},
                    {"awarded_at", "是", "授予日期，格式 YYYY-MM-DD（或 Excel 日期单元格）"},
                    {"red_doc_url", "否", "红头文件/官方文件链接，最长 512 字符"},
                    {"", "", ""},
                    {"整体要求", "", "第 1 行为表头（不导入）；单次 ≤500 行；仅支持 xlsx；坏行不阻断好行"},
                    {"审核定级", "", "院系管理员导入 → 校级复核；校级/系统管理员导入 → 直接通过"},
            };
            for (int i = 0; i < rules.length; i++) {
                Row r = guide.createRow(i);
                for (int j = 0; j < rules[i].length; j++) {
                    r.createCell(j).setCellValue(rules[i][j]);
                }
            }
            guide.setColumnWidth(0, 16 * 256);
            guide.setColumnWidth(1, 8 * 256);
            guide.setColumnWidth(2, 88 * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "模板生成失败");
        }
    }

    /** POST /admin/honors/import：同步逐行校验建荣誉；college_admin→pending_second，school/system→approved */
    @Transactional
    public ImportResult importHonors(AuthPrincipal operator, MultipartFile file) {
        requireAdminRole(operator);
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "file 不能为空");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!filename.toLowerCase().endsWith(".xlsx")) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "仅支持 xlsx 文件");
        }

        List<String[]> dataRows = parseExcel(file);
        if (dataRows.size() > IMPORT_MAX_ROWS) {
            throw new BizException(ErrorCode.BATCH_LIMIT_EXCEEDED,
                    "单次导入不超过 " + IMPORT_MAX_ROWS + " 行（当前 " + dataRows.size() + " 行）");
        }

        String status = "college_admin".equals(operator.role()) ? "pending_second" : "approved";
        String stage = "approved".equals(status) ? null : "second";

        int success = 0;
        List<ImportErrorItem> errors = new ArrayList<>();
        for (String[] row : dataRows) {
            int rowNo = Integer.parseInt(row[6]);
            String studentId = row[0];
            String error = validateAndCreate(operator, row, status, stage);
            if (error == null) {
                success++;
            } else {
                errors.add(new ImportErrorItem(rowNo, studentId, error));
            }
        }
        return new ImportResult(dataRows.size(), success, errors.size(), errors);
    }

    /** 逐行校验并建荣誉；返回 null=成功，否则返回错误原因（坏行不阻断） */
    private String validateAndCreate(AuthPrincipal operator, String[] row, String status, String stage) {
        String studentId = row[0];
        String title = row[1];
        String category = row[2];
        String level = row[3];
        String redDocUrl = row[5];

        if (studentId.isBlank()) {
            return "学号为空";
        }
        if (title.isBlank()) {
            return "标题为空";
        }
        if (title.length() > 128) {
            return "标题最长 128 字符";
        }
        if (category.isBlank() || !dictItemRepository.existsByTypeAndValue(DICT_TYPE_HONOR_CATEGORY, category)) {
            return "荣誉类别不在字典内: " + category;
        }
        if (level.isBlank() || level.length() > 32) {
            return "级别为空或超长";
        }
        LocalDate awardedAt;
        try {
            awardedAt = LocalDate.parse(row[4]);
        } catch (DateTimeParseException e) {
            return "授予日期格式错误（须 YYYY-MM-DD）";
        }
        if (redDocUrl.length() > 512) {
            return "红头文件链接最长 512 字符";
        }

        Map<String, Object> user = findActiveUser(studentId);
        if (user == null) {
            return "学号不存在于校友库或账号未激活";
        }
        Long userId = ((Number) user.get("id")).longValue();

        if (honorRepository.existsByUserIdAndTitleAndAwardedAtAndStatusIn(
                userId, title, awardedAt, IDEMPOTENT_STATUSES)) {
            return "同人同题同日荣誉已存在";
        }

        Honor honor = new Honor();
        honor.setUserId(userId);
        honor.setCreatedBy(operator.userId());
        honor.setRedDocUrl(redDocUrl.isEmpty() ? null : redDocUrl);
        honor.setTitle(title);
        honor.setTitlePinyin(PinyinUtils.toFullPinyin(title));
        honor.setTitlePinyinAbbr(PinyinUtils.toAbbr(title));
        honor.setCategory(category);
        honor.setLevel(level);
        honor.setAwardedAt(awardedAt);
        honor.setStatus(status);
        honor = honorRepository.saveAndFlush(honor);

        if (stage != null) {
            Review review = new Review();
            review.setBizType(BIZ_TYPE_HONOR);
            review.setBizId(honor.getId());
            review.setSubmitterId(userId);
            review.setStage(stage);
            review.setStatus("pending");
            review = reviewRepository.save(review);

            ReviewLog log = new ReviewLog();
            log.setReviewId(review.getId());
            log.setOperatorId(operator.userId());
            log.setAction(ReviewLog.ACTION_CREATE);
            reviewLogRepository.save(log);
        }
        return null;
    }

    /** 解析 xlsx 数据行（跳过表头与整行空白；尾列附带 Excel 原始行号） */
    private List<String[]> parseExcel(MultipartFile file) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(file.getBytes()))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            List<String[]> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String[] cells = new String[IMPORT_HEADERS.length + 1];
                for (int c = 0; c < IMPORT_HEADERS.length; c++) {
                    cells[c] = cellValue(row, c, formatter);
                }
                boolean allBlank = true;
                for (int c = 0; c < IMPORT_HEADERS.length; c++) {
                    if (!cells[c].isBlank()) {
                        allBlank = false;
                        break;
                    }
                }
                if (allBlank) {
                    continue;
                }
                cells[IMPORT_HEADERS.length] = String.valueOf(i + 1); // Excel 原始行号（1-based）
                rows.add(cells);
            }
            return rows;
        } catch (Exception e) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "表格文件无法解析或已损坏");
        }
    }

    /** 单元格取值：日期单元格 → YYYY-MM-DD；其余按显示文本 */
    private String cellValue(Row row, int col, DataFormatter formatter) {
        var cell = row.getCell(col);
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return formatter.formatCellValue(cell).trim();
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private void requireAdminRole(AuthPrincipal operator) {
        if (operator == null || !ADMIN_ROLES.contains(operator.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅院系/校级/系统管理员可访问荣誉管理端");
        }
    }

    /** college_id → dicts(type='college').label；不存在返回 null（不过滤） */
    private String resolveCollegeLabel(Long collegeId) {
        if (collegeId == null) {
            return null;
        }
        Optional<com.alumni.honors.entity.DictItem> dict =
                dictItemRepository.findByTypeAndId(DICT_TYPE_COLLEGE, collegeId);
        return dict.map(com.alumni.honors.entity.DictItem::getLabel).orElse(null);
    }

    /** 操作者本院文本（users → alumni_base.college） */
    private String resolveOperatorCollege(Long userId) {
        List<String> colleges = jdbcTemplate.queryForList(
                OPERATOR_COLLEGE_SQL, String.class, userId);
        return colleges.isEmpty() ? null : colleges.get(0);
    }

    /** 学号 → 已激活在校生/校友账号；不存在或未激活返回 null */
    private Map<String, Object> findActiveUser(String studentId) {
        List<Map<String, Object>> users = jdbcTemplate.queryForList(USER_BY_STUDENT_SQL, studentId);
        if (users.size() != 1) {
            return null;
        }
        Map<String, Object> user = users.get(0);
        return "active".equals(user.get("status")) ? user : null;
    }

    /** 组合 WHERE 条件与参数（列表/导出/计数共用） */
    private ListParams buildWhere(String keyword, String studentId, String collegeLabel,
                                  String category, String level, String status, String screenStatus,
                                  Integer year, Boolean hasRedDoc) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (collegeLabel != null) {
            where.append(" AND ab.college = ?");
            args.add(collegeLabel);
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            where.append(" AND (ab.real_name LIKE ? OR ab.student_id LIKE ?")
                    .append(" OR ab.real_name_pinyin LIKE ? OR ab.real_name_pinyin_abbr LIKE ?")
                    .append(" OR h.title LIKE ? OR h.title_pinyin LIKE ? OR h.title_pinyin_abbr LIKE ?)");
            for (int i = 0; i < 7; i++) {
                args.add(like);
            }
        }
        if (studentId != null && !studentId.isBlank()) {
            where.append(" AND ab.student_id = ?");
            args.add(studentId.trim());
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND h.category = ?");
            args.add(category.trim());
        }
        if (level != null && !level.isBlank()) {
            where.append(" AND h.level = ?");
            args.add(level.trim());
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND h.status = ?");
            args.add(status.trim());
        }
        if (screenStatus != null && !screenStatus.isBlank()) {
            where.append(" AND h.screen_status = ?");
            args.add(screenStatus.trim());
        }
        if (year != null) {
            where.append(" AND YEAR(h.awarded_at) = ?");
            args.add(year);
        }
        if (hasRedDoc != null) {
            where.append(hasRedDoc
                    ? " AND h.red_doc_url IS NOT NULL AND h.red_doc_url <> ''"
                    : " AND (h.red_doc_url IS NULL OR h.red_doc_url = '')");
        }
        return new ListParams(where.toString(), args);
    }

    /** 结果行 → 列表项 DTO */
    private AdminHonorItem mapItem(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        java.sql.Date awardedAt = rs.getDate("awarded_at");
        java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
        return new AdminHonorItem(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("student_id"),
                rs.getString("real_name"),
                rs.getString("college"),
                rs.getObject("grad_year") == null ? null : rs.getInt("grad_year"),
                rs.getString("team_id"),
                rs.getString("title"),
                rs.getString("category"),
                rs.getString("level"),
                awardedAt == null ? null : awardedAt.toLocalDate().toString(),
                rs.getString("status"),
                rs.getString("screen_status"),
                rs.getString("red_doc_url"),
                rs.getObject("created_by") == null ? null : String.valueOf(rs.getLong("created_by")),
                rs.getString("created_by_name"),
                createdAt == null ? null : createdAt.toInstant().toString());
    }

    /** WHERE 子句与参数包 */
    private record ListParams(String where, List<Object> args) {
    }
}
