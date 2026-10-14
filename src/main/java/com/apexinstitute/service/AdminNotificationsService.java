package com.apexinstitute.service;

import com.apexinstitute.dto.AdminNotificationsDto;
import com.apexinstitute.dto.AdminNotificationsDto.NotificationItem;
import com.apexinstitute.dto.AdminNotificationsDto.Stats;
import com.apexinstitute.dto.CreateNotificationRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminNotificationsService {

    private final JdbcTemplate jdbc;

    public AdminNotificationsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /* ---------- GET /api/admin/notifications ---------- */
    public AdminNotificationsDto list() {

        List<NotificationItem> items = jdbc.query("""
                SELECT n.id, n.title, n.message, n.audience, n.class_id,
                       n.created_at,
                       u.full_name AS author,
                       c.name AS class_name,
                       (SELECT COUNT(*) FROM notification_recipients nr
                        WHERE nr.notification_id = n.id) AS recipient_count
                FROM notifications n
                JOIN users u ON u.id = n.created_by
                LEFT JOIN classes c ON c.id = n.class_id
                ORDER BY n.created_at DESC
                """,
                (rs, i) -> {
                    Timestamp ts = rs.getTimestamp("created_at");
                    LocalDateTime dt = ts == null ? LocalDateTime.now() : ts.toLocalDateTime();
                    String audience = rs.getString("audience");
                    String classLabel = "class".equals(audience)
                            ? rs.getString("class_name")
                            : null;
                    return new NotificationItem(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getString("message"),
                            audience,
                            classLabel,
                            rs.getString("author"),
                            dt.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                            dt.format(DateTimeFormatter.ofPattern("HH:mm")),
                            rs.getInt("recipient_count"));
                });

        int general  = (int) items.stream().filter(x -> "general".equals(x.audience())).count();
        int students = (int) items.stream().filter(x -> "students".equals(x.audience())).count();
        int parents  = (int) items.stream().filter(x -> "parents".equals(x.audience())).count();
        int teachers = (int) items.stream().filter(x -> "teachers".equals(x.audience())).count();
        int classes  = (int) items.stream().filter(x -> "class".equals(x.audience())).count();

        Stats stats = new Stats(items.size(), general, students, parents, teachers, classes);

        return new AdminNotificationsDto(stats, items);
    }

    /* ---------- POST /api/admin/notifications ---------- */
    @Transactional
    public Long create(CreateNotificationRequest req) {

        Long authorId = Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getName());

        String audience = req.getAudience();
        Long classId = "class".equals(audience) ? req.getClassId() : null;

        if ("class".equals(audience)) {
            if (classId == null)
                throw new RuntimeException("Please select a class");
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM classes WHERE id = ?", Integer.class, classId);
            if (exists == null || exists == 0)
                throw new RuntimeException("Class not found");
        }

        // 1. Insert the notification
        jdbc.update("""
                INSERT INTO notifications
                    (title, message, audience, class_id, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, SYSDATETIME())
                """,
                req.getTitle().trim(),
                req.getMessage().trim(),
                audience,
                classId,
                authorId);

        Long notificationId = jdbc.queryForObject(
                "SELECT TOP 1 id FROM notifications ORDER BY id DESC", Long.class);

        // 2. Compute recipient user ids based on audience
        List<Long> recipientUserIds = resolveRecipients(audience, classId);

        // 3. Fan out to notification_recipients
        for (Long userId : recipientUserIds) {
            jdbc.update("""
                    INSERT INTO notification_recipients
                        (notification_id, user_id, is_read)
                    VALUES (?, ?, 0)
                    """, notificationId, userId);
        }

        return notificationId;
    }

    /* ---------- DELETE /api/admin/notifications/{id} ---------- */
    @Transactional
    public void delete(Long id) {
        // notification_recipients cascade on notification_id, so just delete parent
        int deleted = jdbc.update("DELETE FROM notifications WHERE id = ?", id);
        if (deleted == 0) throw new RuntimeException("Notification not found");
    }

    /* ---------- Recipient resolution ---------- */
    private List<Long> resolveRecipients(String audience, Long classId) {
        switch (audience) {
            case "general":
                return jdbc.queryForList(
                        "SELECT id FROM users WHERE is_active = 1", Long.class);

            case "students":
                return jdbc.queryForList(
                        "SELECT id FROM users WHERE role = 'STUDENT' AND is_active = 1",
                        Long.class);

            case "parents":
                return jdbc.queryForList(
                        "SELECT id FROM users WHERE role = 'PARENT' AND is_active = 1",
                        Long.class);

            case "teachers":
                return jdbc.queryForList(
                        "SELECT id FROM users WHERE role = 'TEACHER' AND is_active = 1",
                        Long.class);

            case "class":
                // Students enrolled in the class + their linked parents
                List<Long> result = new ArrayList<>();
                // students
                result.addAll(jdbc.queryForList("""
                        SELECT DISTINCT u.id
                        FROM enrollments en
                        JOIN students s ON s.id = en.student_id
                        JOIN users    u ON u.id = s.user_id
                        WHERE en.class_id = ? AND en.is_active = 1 AND u.is_active = 1
                        """, Long.class, classId));
                // their parents
                result.addAll(jdbc.queryForList("""
                        SELECT DISTINCT pu.id
                        FROM enrollments en
                        JOIN students s ON s.id = en.student_id
                        JOIN parent_child pc ON pc.student_id = s.id
                        JOIN parents p ON p.id = pc.parent_id
                        JOIN users pu ON pu.id = p.user_id
                        WHERE en.class_id = ? AND en.is_active = 1 AND pu.is_active = 1
                        """, Long.class, classId));
                return result;

            default:
                throw new RuntimeException("Invalid audience: " + audience);
        }
    }
}