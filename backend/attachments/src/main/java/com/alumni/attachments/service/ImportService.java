package com.alumni.attachments.service;

import com.alumni.attachments.common.BizException;
import com.alumni.attachments.common.ErrorCode;
import com.alumni.attachments.dto.ImportDtos.Created;
import com.alumni.attachments.dto.ImportDtos.ErrorItem;
import com.alumni.attachments.dto.ImportDtos.ErrorList;
import com.alumni.attachments.dto.ImportDtos.Result;
import com.alumni.attachments.dto.ImportDtos.TaskItem;
import com.alumni.attachments.dto.ImportDtos.TaskList;
import com.alumni.attachments.entity.Attachment;
import com.alumni.attachments.entity.ImportTask;
import com.alumni.attachments.repository.AttachmentRepository;
import com.alumni.attachments.repository.ImportErrorRepository;
import com.alumni.attachments.repository.ImportTaskRepository;
import com.alumni.common.security.AuthPrincipal;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入（API 规范第十一章，挂载 attachments 模块）：
 * - 上传 csv/xls/xlsx（扩展名+内容嗅探，≤10MB，≤2000 行），受理后异步入 alumni_base
 * - 原表文件存 attachments 行（biz_type=import，ref_id=任务 id），便于回溯且不被孤儿清理
 * - GET /imports/template 动态生成 xlsx 模板（数据 sheet + 填写说明 sheet）
 */
@Service
public class ImportService {

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_ROWS = 2000;
    /** 表头列（顺序固定，与模板一致） */
    static final String[] HEADERS = {"student_id", "real_name", "id_card", "phone", "college", "major", "grad_year"};

    private final ImportTaskRepository importTaskRepository;
    private final ImportErrorRepository importErrorRepository;
    private final AttachmentRepository attachmentRepository;
    private final FileStorageService storageService;
    private final ImportAsyncWorker asyncWorker;

    public ImportService(ImportTaskRepository importTaskRepository,
                         ImportErrorRepository importErrorRepository,
                         AttachmentRepository attachmentRepository,
                         FileStorageService storageService,
                         ImportAsyncWorker asyncWorker) {
        this.importTaskRepository = importTaskRepository;
        this.importErrorRepository = importErrorRepository;
        this.attachmentRepository = attachmentRepository;
        this.storageService = storageService;
        this.asyncWorker = asyncWorker;
    }

    /** 解析后的数据行（rowNo 为原始行号 1-based，表头为 1） */
    public record DataRow(int rowNo, List<String> cells) {
    }

    /** POST /imports：校验 → 留存原表 → 建任务 → 异步处理 → 立即返回 import_id */
    @Transactional
    public Created create(MultipartFile file, AuthPrincipal uploader) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "file 不能为空");
        }
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件读取失败");
        }
        if (data.length > MAX_FILE_BYTES) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE, "文件大小超限（≤10MB）");
        }
        String format = sniffFormat(originalName, data);
        List<DataRow> rows = parse(format, data);
        if (rows.size() > MAX_ROWS) {
            throw new BizException(ErrorCode.BATCH_LIMIT_EXCEEDED, "单次最多导入 " + MAX_ROWS + " 行");
        }

        // 原表留存（先 unreferenced，任务建行后回填 ref_id，防孤儿清理）
        String ext = switch (format) {
            case "xlsx" -> "xlsx";
            case "xls" -> "xls";
            default -> "csv";
        };
        String url = storageService.save("import", ext, data);
        Attachment attachment = new Attachment();
        attachment.setBizType("import");
        attachment.setStatus("unreferenced");
        attachment.setUrl(url);
        attachment.setName(originalName);
        attachment.setSize((long) data.length);
        attachment.setMime(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        attachment.setUploaderId(uploader.userId());
        attachmentRepository.save(attachment);

        ImportTask task = new ImportTask();
        task.setUploaderId(uploader.userId());
        task.setFilename(originalName);
        task.setFileSize((long) data.length);
        task.setTotal(rows.size());
        importTaskRepository.save(task);

        attachment.setRefId(task.getId());
        attachment.setStatus("referenced");
        attachment.setReferencedAt(java.time.Instant.now());
        attachmentRepository.save(attachment);

        asyncWorker.process(task.getId(), rows);
        return new Created(task.getId());
    }

    /** GET /imports/{id}：导入结果（仅本人上传的任务可见） */
    public Result result(Long id, AuthPrincipal user) {
        ImportTask task = mustOwn(id, user);
        return new Result(
                task.getId(),
                task.getStatus(),
                task.getTotal(),
                task.getSuccess(),
                task.getFailed(),
                task.getFilename(),
                task.getFileSize(),
                task.getCreatedAt(),
                task.getFinishedAt()
        );
    }

    /** GET /imports：导入任务列表（仅本人上传的任务，按创建时间倒序） */
    public TaskList list(AuthPrincipal user) {
        List<TaskItem> items = importTaskRepository.findByUploaderIdOrderByCreatedAtDesc(user.userId()).stream()
                .map(t -> new TaskItem(
                        t.getId(),
                        t.getStatus(),
                        t.getTotal(),
                        t.getSuccess(),
                        t.getFailed(),
                        t.getFilename(),
                        t.getFileSize(),
                        t.getCreatedAt(),
                        t.getFinishedAt()
                ))
                .toList();
        return new TaskList(items, items.size());
    }

    /** GET /imports/{id}/errors：错误行报告（仅本人上传的任务可见） */
    public ErrorList errors(Long id, AuthPrincipal user) {
        mustOwn(id, user);
        List<ErrorItem> list = importErrorRepository.findByImportIdOrderByRowNoAscIdAsc(id).stream()
                .map(e -> new ErrorItem(e.getRowNo(), e.getStudentId(), e.getReason()))
                .toList();
        return new ErrorList(list);
    }

    /** GET /imports/template：生成 xlsx 模板（数据 sheet 表头+示例；说明 sheet 填写规则） */
    public byte[] template() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet data = wb.createSheet("导入数据");
            Row header = data.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
                data.setColumnWidth(i, 16 * 256);
            }
            Row sample = data.createRow(1);
            String[] sampleValues = {"2021000101", "张三", "110101200301071234", "13900001234", "计算机学院", "软件工程", "2025"};
            for (int i = 0; i < sampleValues.length; i++) {
                sample.createCell(i).setCellValue(sampleValues[i]);
            }

            Sheet guide = wb.createSheet("填写说明");
            String[][] rules = {
                    {"列", "必填", "规则"},
                    {"student_id", "是", "学号，最长 32 字符；校友库内唯一，重复行记为错误行"},
                    {"real_name", "是", "真实姓名，最长 64 字符"},
                    {"id_card", "是", "18 位数字身份证号"},
                    {"phone", "是", "11 位手机号（1 开头）"},
                    {"college", "否", "学院名称（文本），最长 64 字符"},
                    {"major", "否", "专业名称（文本），最长 64 字符"},
                    {"grad_year", "是", "4 位毕业年份数字（如 2025）"},
                    {"", "", ""},
                    {"整体要求", "", "第 1 行为表头（不导入）；单次 ≤2000 行；文件 ≤10MB；支持 csv(UTF-8)/xls/xlsx；坏行不阻断好行"},
            };
            for (int i = 0; i < rules.length; i++) {
                Row r = guide.createRow(i);
                for (int j = 0; j < rules[i].length; j++) {
                    r.createCell(j).setCellValue(rules[i][j]);
                }
            }
            guide.setColumnWidth(0, 14 * 256);
            guide.setColumnWidth(1, 8 * 256);
            guide.setColumnWidth(2, 80 * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "模板生成失败");
        }
    }

    // ---------- 内部：格式识别与解析 ----------

    /** 扩展名 + 内容嗅探：xlsx=ZIP(PK)，xls=OLE2(D0CF11E0)，csv=UTF-8 文本；其余拒绝 */
    private String sniffFormat(String filename, byte[] data) {
        String ext = "";
        int dot = filename.lastIndexOf('.');
        if (dot >= 0) {
            ext = filename.substring(dot + 1).toLowerCase();
        }
        boolean zipMagic = data.length >= 4 && data[0] == 0x50 && data[1] == 0x4B;
        boolean ole2Magic = data.length >= 8 && (data[0] & 0xFF) == 0xD0 && (data[1] & 0xFF) == 0xCF
                && (data[2] & 0xFF) == 0x11 && (data[3] & 0xFF) == 0xE0;
        if (zipMagic && "xlsx".equals(ext)) {
            return "xlsx";
        }
        if (ole2Magic && "xls".equals(ext)) {
            return "xls";
        }
        if ("csv".equals(ext) && !zipMagic && !ole2Magic) {
            return "csv";
        }
        throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "文件格式不支持（仅 csv/xls/xlsx，扩展名与内容须一致）");
    }

    /** 解析全部数据行（跳过表头） */
    private List<DataRow> parse(String format, byte[] data) {
        return switch (format) {
            case "xlsx", "xls" -> parseExcel(data);
            default -> parseCsv(data);
        };
    }

    private List<DataRow> parseExcel(byte[] data) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(data))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            List<DataRow> rows = new ArrayList<>();
            // 数据从第 2 行起（第 1 行表头）；rowNo 用 Excel 原始行号（1-based）
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                List<String> cells = new ArrayList<>(HEADERS.length);
                for (int c = 0; c < HEADERS.length; c++) {
                    cells.add(row.getCell(c) != null ? formatter.formatCellValue(row.getCell(c)).trim() : "");
                }
                if (cells.stream().allMatch(String::isBlank)) {
                    continue; // 整行空白跳过
                }
                rows.add(new DataRow(i + 1, cells));
            }
            return rows;
        } catch (Exception e) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "表格文件无法解析或已损坏");
        }
    }

    /**
     * 解析 CSV（RFC 4180）：严格 UTF-8 解码（规范第十一章要求；GBK 等非 UTF-8 直接 400，防乱码入库）；
     * 字符流状态机处理引号，引号内换行不切断。
     */
    private List<DataRow> parseCsv(byte[] data) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(data)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "csv 必须为 UTF-8 编码");
        }
        if (text.startsWith("﻿")) {
            text = text.substring(1); // UTF-8 BOM 剥离
        }

        // 全文字符流解析：allRows[0] 为表头；数据行从索引 1 起，rowNo 为逻辑行号（1-based）
        List<List<String>> allRows = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inQuotes) {
                if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (ch == '"') {
                    inQuotes = false;
                } else {
                    current.append(ch); // 引号内换行/逗号原样保留
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                cells.add(current.toString().trim());
                current.setLength(0);
                allRows.add(cells);
                cells = new ArrayList<>();
            } else {
                current.append(ch);
            }
        }
        if (current.length() > 0 || !cells.isEmpty()) {
            cells.add(current.toString().trim());
            allRows.add(cells);
        }

        List<DataRow> rows = new ArrayList<>();
        for (int i = 1; i < allRows.size(); i++) {
            List<String> row = new ArrayList<>(allRows.get(i));
            while (row.size() < HEADERS.length) {
                row.add("");
            }
            if (row.stream().allMatch(String::isBlank)) {
                continue;
            }
            rows.add(new DataRow(i + 1, row.subList(0, HEADERS.length)));
        }
        return rows;
    }

    private ImportTask mustOwn(Long id, AuthPrincipal user) {
        ImportTask task = importTaskRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "导入任务不存在"));
        if (!task.getUploaderId().equals(user.userId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
        }
        return task;
    }
}
