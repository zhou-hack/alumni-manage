package com.alumni.notifications.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 待办超时提醒（[M14] todo 类型站内消息，服务端自动生成）。
 *
 * <p>每日 09:00 扫描 reviews 表：stage+status=pending 且 created_at 超 48 小时的在途单据，
 * 给对应审核角色各发一条汇总通知（每个审核人每日每角色至多一条）：</p>
 * <ul>
 *   <li>stage='first'     → 全体 active 的 college_admin（待院系初审）</li>
 *   <li>stage='second'    → 全体 active 的 school_admin（待校级终审）</li>
 *   <li>stage='counselor' → 按 counselor_scopes 覆盖关系命中的 active 导员（届别∪专业∪学号并集且同学院）</li>
 * </ul>
 *
 * <p>红线：content 仅含汇总单数，不得包含学号。提醒后写 audit_logs（action='todo_remind'）。
 * JdbcTemplate 直读直写，不开 JPA 事务；整体 try/catch 兜底，定时任务绝不外抛异常。</p>
 */
@Service
public class TodoReminderService {

    private static final Logger log = LoggerFactory.getLogger(TodoReminderService.class);

    private static final String TYPE_TODO = "todo";
    private static final String TITLE = "待办超时提醒";
    private static final String TIMEOUT_CLAUSE = "created_at < DATE_SUB(NOW(), INTERVAL 48 HOUR)";

    private final JdbcTemplate jdbcTemplate;

    public TodoReminderService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 每日 09:00 执行；整体 try/catch 防止定时任务炸掉 */
    @Scheduled(cron = "0 0 9 * * *")
    public void remindDaily() {
        try {
            int collegeAdmins = remindRole("first", "college_admin", "院系初审");
            int schoolAdmins = remindRole("second", "school_admin", "校级终审");
            int counselors = remindCounselors();
            if (collegeAdmins + schoolAdmins + counselors > 0) {
                writeAuditLog(collegeAdmins, schoolAdmins, counselors);
            }
        } catch (Exception e) {
            log.error("待办超时提醒任务执行失败", e);
        }
    }

    /** first/second 阶段：超时单据数 >0 时给全体 active 对应角色各发一条汇总通知，返回通知人数 */
    private int remindRole(String stage, String role, String stageLabel) {
        try {
            Long overdue = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM reviews WHERE stage = ? AND status = 'pending' AND " + TIMEOUT_CLAUSE,
                    Long.class, stage);
            if (overdue == null || overdue <= 0) {
                return 0;
            }
            List<Long> adminIds = jdbcTemplate.queryForList(
                    "SELECT id FROM users WHERE role = ? AND status = 'active'", Long.class, role);
            String content = "您有 " + overdue + " 条待" + stageLabel + "单据已超 48 小时";
            for (Long adminId : adminIds) {
                insertTodoNotification(adminId, content);
            }
            return adminIds.size();
        } catch (Exception e) {
            log.error("待办超时提醒失败：stage={}, role={}", stage, role, e);
            return 0;
        }
    }

    /**
     * counselor 阶段：按覆盖导员汇总（counselor_scopes 命中提交人 alumni_base 的
     * 届别/专业/学号且同学院，导员 users.status='active'），每个导员一条，返回通知人数。
     */
    private int remindCounselors() {
        try {
            String sql = """
                    SELECT cs.counselor_id AS counselor_id, COUNT(DISTINCT r.id) AS n
                    FROM reviews r
                    JOIN users su ON su.id = r.submitter_id
                    JOIN alumni_base ab ON ab.id = su.alumni_base_id
                    JOIN dicts dc ON dc.type = 'college' AND dc.label = ab.college
                    JOIN counselor_scopes cs ON cs.college_id = dc.id
                        AND (cs.student_id = ab.student_id
                             OR cs.grad_year = ab.grad_year
                             OR cs.major_id IN (SELECT dm.id FROM dicts dm
                                                WHERE dm.type = 'major' AND dm.label = ab.major))
                    JOIN users cu ON cu.id = cs.counselor_id AND cu.status = 'active'
                    WHERE r.stage = 'counselor' AND r.status = 'pending'
                        AND r.created_at < DATE_SUB(NOW(), INTERVAL 48 HOUR)
                    GROUP BY cs.counselor_id
                    """;
            List<CounselorOverdue> rows = jdbcTemplate.query(sql,
                    (rs, rowNum) -> new CounselorOverdue(rs.getLong("counselor_id"), rs.getLong("n")));
            for (CounselorOverdue row : rows) {
                insertTodoNotification(row.counselorId(),
                        "您有 " + row.overdue() + " 条待导员初审单据已超 48 小时");
            }
            return rows.size();
        } catch (Exception e) {
            log.error("待办超时提醒失败：stage=counselor", e);
            return 0;
        }
    }

    private void insertTodoNotification(long userId, String content) {
        jdbcTemplate.update(
                "INSERT INTO notifications (user_id, type, title, content, biz_type, biz_id) VALUES (?, ?, ?, ?, NULL, NULL)",
                userId, TYPE_TODO, TITLE, content);
    }

    /** 提醒后写审计日志：action='todo_remind'，detail 含各角色通知人数（系统动作 user 为 NULL） */
    private void writeAuditLog(int collegeAdmins, int schoolAdmins, int counselors) {
        try {
            String detail = "{\"college_admin\":" + collegeAdmins
                    + ",\"school_admin\":" + schoolAdmins
                    + ",\"counselor\":" + counselors + "}";
            jdbcTemplate.update(
                    "INSERT INTO audit_logs (user_id, user_role, action, biz_type, biz_id, detail, ip)"
                            + " VALUES (NULL, NULL, 'todo_remind', 'review', NULL, ?, NULL)",
                    detail);
        } catch (Exception e) {
            log.error("待办超时提醒审计日志写入失败", e);
        }
    }

    private record CounselorOverdue(long counselorId, long overdue) {
    }
}
