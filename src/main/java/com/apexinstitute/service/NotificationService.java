package com.apexinstitute.service;

import com.apexinstitute.dto.NotificationListDto;
import com.apexinstitute.dto.NotificationListDto.Item;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class NotificationService {

    private final JdbcTemplate jdbc;

    public NotificationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private Long currentUserId() {
        return Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getName());
    }

    /* ---------- GET /api/notifications ---------- */
    public NotificationListDto list() {
        Long userId = currentUserId();

        List<Item> items = jdbc.query("""
                SELECT n.id, n.title, n.message, n.audience, n.created_at,
                       u.full_name AS author,
                       nr.is_read
                FROM notifications n
                JOIN notification_recipients nr ON nr.notification_id = n.id
                JOIN users u ON u.id = n.created_by
                WHERE nr.user_id = ?
                ORDER BY n.created_at DESC
                """,
                (rs, i) -> {
                    Timestamp ts = rs.getTimestamp("created_at");
                    LocalDateTime dt = ts == null ? LocalDateTime.now() : ts.toLocalDateTime();
                    return new Item(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getString("message"),
                            rs.getString("audience"),
                            rs.getString("author"),
                            humanTime(dt),
                            dt.format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")),
                            rs.getBoolean("is_read"));
                },
                userId);

        int unread = (int) items.stream().filter(x -> !x.read()).count();
        return new NotificationListDto(items.size(), unread, items);
    }

    /* ---------- PUT /api/notifications/{id}/read ---------- */
    @Transactional
    public void markRead(Long notificationId) {
        Long userId = currentUserId();
        jdbc.update("""
                UPDATE notification_recipients
                SET is_read = 1, read_at = SYSDATETIME()
                WHERE notification_id = ? AND user_id = ? AND is_read = 0
                """, notificationId, userId);
    }

    /* ---------- PUT /api/notifications/read-all ---------- */
    @Transactional
    public int markAllRead() {
        Long userId = currentUserId();
        return jdbc.update("""
                UPDATE notification_recipients
                SET is_read = 1, read_at = SYSDATETIME()
                WHERE user_id = ? AND is_read = 0
                """, userId);
    }

    private static String humanTime(LocalDateTime dt) {
        Duration d = Duration.between(dt, LocalDateTime.now());
        long m = d.toMinutes();
        if (m < 1) return "Just now";
        if (m < 60) return m + "m ago";
        long h = d.toHours();
        if (h < 24) return h + "h ago";
        long days = d.toDays();
        if (days == 1) return "Yesterday";
        if (days < 7) return days + "d ago";
        return dt.format(DateTimeFormatter.ofPattern("d MMM"));
    }
}