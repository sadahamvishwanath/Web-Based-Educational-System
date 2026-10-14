package com.apexinstitute.service;

import com.apexinstitute.dto.ManagementReportsDto;
import com.apexinstitute.dto.ManagementReportsDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class ManagementReportsService {

    private final JdbcTemplate jdbc;

    public ManagementReportsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public ManagementReportsDto load() {

        // ---------- Income report ----------
        BigDecimal monthTotal = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM payments
                WHERE MONTH(payment_date) = MONTH(SYSDATETIME())
                  AND YEAR(payment_date)  = YEAR(SYSDATETIME())
                """, BigDecimal.class);

        BigDecimal ytdTotal = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM payments
                WHERE YEAR(payment_date) = YEAR(SYSDATETIME())
                """, BigDecimal.class);

        List<MethodBreakdown> byMethod = jdbc.query("""
                SELECT method, COALESCE(SUM(amount), 0) AS total
                FROM payments
                WHERE YEAR(payment_date) = YEAR(SYSDATETIME())
                GROUP BY method
                ORDER BY total DESC
                """,
                (rs, i) -> new MethodBreakdown(
                        rs.getString("method"),
                        round1(rs.getBigDecimal("total").doubleValue())));

        IncomeReport income = new IncomeReport(
                round1(monthTotal == null ? 0 : monthTotal.doubleValue()),
                round1(ytdTotal == null ? 0 : ytdTotal.doubleValue()),
                byMethod);

        // ---------- Income by grade ----------
        List<GradeIncome> byGrade = new ArrayList<>();
        for (String g : List.of("10", "11", "12", "13")) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM students WHERE grade = ? AND is_active = 1
                    """, Integer.class, g);

            BigDecimal inc = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(p.amount), 0)
                    FROM payments p
                    JOIN invoices i ON i.id = p.invoice_id
                    JOIN students s ON s.id = i.student_id
                    WHERE s.grade = ?
                    """, BigDecimal.class, g);

            byGrade.add(new GradeIncome(g,
                    count == null ? 0 : count,
                    round1(inc == null ? 0 : inc.doubleValue())));
        }

        // ---------- Attendance by grade ----------
        List<GradeAttendance> attendance = new ArrayList<>();
        for (String g : List.of("10", "11", "12", "13")) {
            Double pct = jdbc.queryForObject("""
                    SELECT CASE WHEN COUNT(*) = 0 THEN 0.0
                                ELSE SUM(CASE WHEN a.status='Present' THEN 1 ELSE 0 END) * 100.0 / COUNT(*)
                           END
                    FROM attendance a
                    JOIN students s ON s.id = a.student_id
                    WHERE s.grade = ?
                    """, Double.class, g);
            attendance.add(new GradeAttendance(g, round1(pct == null ? 0 : pct)));
        }

        // ---------- Defaulters ----------
        List<Defaulter> defaulters = jdbc.query("""
                SELECT u.full_name, s.student_id, s.grade,
                       i.total_amount, i.due_date
                FROM invoices i
                JOIN students s ON s.id = i.student_id
                JOIN users u ON u.id = s.user_id
                WHERE i.status = 'unpaid'
                ORDER BY i.due_date
                """,
                (rs, i) -> new Defaulter(
                        rs.getString("full_name"),
                        rs.getString("student_id"),
                        rs.getString("grade"),
                        rs.getBigDecimal("total_amount").doubleValue(),
                        rs.getDate("due_date").toLocalDate()
                                .format(DateTimeFormatter.ofPattern("d MMM yyyy"))));

        return new ManagementReportsDto(income, byGrade, attendance, defaulters);
    }

    private static double round1(double n) { return Math.round(n * 10.0) / 10.0; }
}