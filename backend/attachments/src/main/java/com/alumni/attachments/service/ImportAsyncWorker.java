package com.alumni.attachments.service;

import com.alumni.attachments.entity.ImportTask;
import com.alumni.attachments.repository.ImportErrorRepository;
import com.alumni.attachments.repository.ImportTaskRepository;
import com.alumni.attachments.entity.ImportError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 导入任务异步处理器：逐行校验，好行入 alumni_base，坏行记 import_errors（坏行不阻断好行）。
 * 独立于 ImportService 的 bean——@Async 自调用不经过代理，必须跨 bean 调用。
 * 不开类级事务：JdbcTemplate / Repository.save 逐行自动提交，天然满足坏行不回滚好行。
 */
@Component
public class ImportAsyncWorker {

    private static final Logger log = LoggerFactory.getLogger(ImportAsyncWorker.class);

    /** 身份证号仅接受 18 位纯数字（与导入模板及批量导入规范一致，X 结尾记为坏行） */
    private static final Pattern ID_CARD = Pattern.compile("^\\d{18}$");
    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");
    private static final Pattern GRAD_YEAR = Pattern.compile("^\\d{4}$");

    private final ImportTaskRepository importTaskRepository;
    private final ImportErrorRepository importErrorRepository;
    private final JdbcTemplate jdbcTemplate;

    public ImportAsyncWorker(ImportTaskRepository importTaskRepository,
                             ImportErrorRepository importErrorRepository,
                             JdbcTemplate jdbcTemplate) {
        this.importTaskRepository = importTaskRepository;
        this.importErrorRepository = importErrorRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 异步执行：整体完成后更新任务计数与 done 状态 */
    @Async("importExecutor")
    public void process(Long taskId, List<ImportService.DataRow> rows) {
        try {
            doProcess(taskId, rows);
        } catch (Exception e) {
            // 整体异常兜底：不吞异常痕迹，任务置 done 避免前端无限轮询
            log.error("导入任务异常中断：id={}，已处理行结果保留", taskId, e);
            importTaskRepository.findById(taskId).ifPresent(t -> {
                t.setStatus("done");
                t.setFinishedAt(java.time.Instant.now());
                importTaskRepository.save(t);
            });
        }
    }

    private void doProcess(Long taskId, List<ImportService.DataRow> rows) {
        int success = 0;
        int failed = 0;
        Set<String> seen = new HashSet<>(); // 批内学号去重
        for (ImportService.DataRow row : rows) {
            String reason = validate(row, seen);
            if (reason == null) {
                try {
                    insertAlumniBase(row);
                    seen.add(row.cells().get(0));
                    success++;
                } catch (Exception e) {
                    // 库级唯一键兜底（学号与库内已有重复）
                    recordError(taskId, row, "学号重复");
                    failed++;
                }
            } else {
                recordError(taskId, row, reason);
                failed++;
            }
        }
        ImportTask task = importTaskRepository.findById(taskId).orElseThrow();
        task.setSuccess(success);
        task.setFailed(failed);
        task.setStatus("done");
        task.setFinishedAt(java.time.Instant.now());
        importTaskRepository.save(task);
        log.info("导入任务完成：id={} total={} success={} failed={}", taskId, rows.size(), success, failed);
    }

    /** 行校验：返回 null 为合法，否则为错误原因 */
    private String validate(ImportService.DataRow row, Set<String> seen) {
        List<String> c = row.cells();
        String studentId = c.get(0);
        String realName = c.get(1);
        String idCard = c.get(2);
        String phone = c.get(3);
        String college = c.get(4);
        String major = c.get(5);
        String gradYear = c.get(6);

        if (studentId.isBlank() || studentId.length() > 32) {
            return "学号缺失或超长（≤32）";
        }
        if (seen.contains(studentId)) {
            return "学号重复";
        }
        if (realName.isBlank() || realName.length() > 64) {
            return "姓名缺失或超长（≤64）";
        }
        if (!ID_CARD.matcher(idCard).matches()) {
            return "身份证号格式错误（18 位）";
        }
        if (!PHONE.matcher(phone).matches()) {
            return "手机号格式错误（11 位 1 开头）";
        }
        if (!GRAD_YEAR.matcher(gradYear).matches()) {
            return "毕业年份格式错误（4 位数字）";
        }
        int year = Integer.parseInt(gradYear);
        int currentYear = java.time.Year.now().getValue();
        if (year < 1900 || year > currentYear + 10) {
            return "毕业年份超出合理范围";
        }
        if (college.length() > 64 || major.length() > 64) {
            return "学院/专业超长（≤64）";
        }
        return null;
    }

    /** 合法行入 alumni_base（uk_alumni_base_student_id 唯一键兜底防并发重复） */
    private void insertAlumniBase(ImportService.DataRow row) {
        jdbcTemplate.update(
                "INSERT INTO alumni_base (student_id, real_name, id_card, phone, college, major, grad_year)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                row.cells().get(0),
                row.cells().get(1),
                row.cells().get(2),
                row.cells().get(3),
                row.cells().get(4).isBlank() ? null : row.cells().get(4),
                row.cells().get(5).isBlank() ? null : row.cells().get(5),
                Integer.parseInt(row.cells().get(6)));
    }

    private void recordError(Long taskId, ImportService.DataRow row, String reason) {
        ImportError error = new ImportError();
        error.setImportId(taskId);
        error.setRowNo(row.rowNo());
        error.setStudentId(row.cells().get(0).isBlank() ? null : row.cells().get(0));
        error.setReason(reason);
        importErrorRepository.save(error);
    }
}
