package com.apexinstitute.service;

import com.apexinstitute.dto.ParentDashboardDto;
import com.apexinstitute.dto.ParentDashboardDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class ParentDashboardService {

    private final JdbcTemplate jdbc;
    private final ParentContext ctx;

    public ParentDashboardService(JdbcTemplate jdbc, ParentContext ctx) {
        this.jdbc = jdbc;
        this.ctx = ctx;
    }

    public ParentDashboardDto load() {
        ParentContext.ParentInfo p = ctx.current();

        // ---------- Children ----------
        List<ChildCard> children = jdbc.query("""
                SELECT s.id, s.student_id, s.grade, u.full_name,
                       (SELECT COUNT(*) FROM enrollments e
                        WHERE e.student_id = s.id AND e.is_active = 1) AS classes,
                       (SELECT COUNT(*) FROM attendance a WHERE a.student_id = s.id) AS total,
                       (SELECT COUNT(*) FROM attendance a
                        WHERE a.student_id = s.id AND a.status='Present') AS present
                FROM parent_child pc
                JOIN students s ON s.id = pc.student_id
                JOIN users u ON u.id = s.user_id
                WHERE pc.parent_id = ?
                ORDER BY u.full_name
                """,
                (rs, i) -> {
                    int total = rs.getInt("total");
                    int present = rs.getInt("present");
                    double pct = total == 0 ? 0.0 : round1(present * 100.0 / total);

                    // Latest published grade for this child
                    LatestGrade lg = latestGrade(rs.getLong("id"));

                    return new ChildCard(
                            rs.getLong("id"),
                            rs.getString("student_id"),
                            rs.getString("full_name"),
                            rs.getString("grade"),
                            pct,
                            rs.getInt("classes"),
                            lg.grade,
                            lg.pct);
                },
                p.id());

        // ---------- Stats ----------
        double avgAtt = children.stream().mapToDouble(ChildCard::attendance)
                .average().orElse(0.0);

        // ---------- Unpaid invoices ----------
        List<UnpaidInvoice> unpaid = jdbc.query("""
                SELECT i.id, i.invoice_no, i.total_amount, i.due_date,
                       u.full_name AS child_name
                FROM invoices i
                JOIN students s ON s.id = i.student_id
                JOIN users u ON u.id = s.user_id
                JOIN parent_child pc ON pc.student_id = s.id
                WHERE pc.parent_id = ? AND i.status = 'unpaid'
                ORDER BY i.due_date
                """,
                (rs, i) -> new UnpaidInvoice(
                        rs.getLong("id"),
                        rs.getString("invoice_no"),
                        rs.getString("child_name"),
                        rs.getBigDecimal("total_amount").doubleValue(),
                        rs.getDate("due_date").toLocalDate()
                                .format(DateTimeFormatter.ofPattern("d MMM yyyy"))),
                p.id());

        double unpaidTotal = unpaid.stream().mapToDouble(UnpaidInvoice::amount).sum();

        // ---------- Alerts (recent notifications for this parent) ----------
        List<AlertItem> alerts = new ArrayList<>();
        jdbc.query("""
                SELECT TOP 5 n.title, n.message, n.created_at
                FROM notifications n
                JOIN notification_recipients nr ON nr.notification_id = n.id
                JOIN users u ON u.id = nr.user_id
                WHERE u.id = (SELECT user_id FROM parents WHERE id = ?)
                ORDER BY n.created_at DESC
                """,
                rs -> {
                    while (rs.next()) {
                        alerts.add(new AlertItem(
                                rs.getString("title"),
                                rs.getString("message"),
                                humanTime(rs.getTimestamp("created_at")),
                                "info"));
                    }
                    return null;
                },
                p.id());

        Stats stats = new Stats(
                children.size(),
                round1(avgAtt),
                unpaid.size(),
                round1(unpaidTotal),
                alerts.size());

        ParentSummary summary = new ParentSummary(p.id(), p.nic(), p.fullName());

        return new ParentDashboardDto(summary, stats, children, unpaid, alerts);
    }

    private LatestGrade latestGrade(Long studentId) {
        return jdbc.query("""
                SELECT TOP 1 er.marks_obtained, e.total_marks
                FROM exam_results er
                JOIN exams e ON e.id = er.exam_id
                WHERE er.student_id = ? AND e.is_published = 1
                ORDER BY e.created_at DESC
                """,
                rs -> {
                    if (!rs.next()) return new LatestGrade("—", 0.0);
                    double marks = rs.getBigDecimal("marks_obtained").doubleValue();
                    int total = rs.getInt("total_marks");
                    double pct = total == 0 ? 0.0 : round1(marks * 100.0 / total);
                    return new LatestGrade(gradeOf(pct), pct);
                },
                studentId);
    }

    private static String gradeOf(double pct) {
        if (pct >= 75) return "A";
        if (pct >= 65) return "B";
        if (pct >= 55) return "C";
        if (pct >= 35) return "S";
        return "F";
    }

    private static double round1(double n) { return Math.round(n * 10.0) / 10.0; }

    private static String humanTime(Timestamp ts) {
        if (ts == null) return "—";
        LocalDateTime dt = ts.toLocalDateTime();
        Duration d = Duration.between(dt, LocalDateTime.now());
        long m = d.toMinutes();
        if (m < 60) return m + "m ago";
        long h = d.toHours();
        if (h < 24) return h + "h ago";
        long days = d.toDays();
        if (days == 1) return "Yesterday";
        if (days < 7) return days + "d ago";
        return dt.format(DateTimeFormatter.ofPattern("d MMM"));
    }

    private record LatestGrade(String grade, double pct) {}
}