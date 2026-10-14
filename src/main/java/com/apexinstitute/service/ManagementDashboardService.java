package com.apexinstitute.service;

import com.apexinstitute.dto.ManagementDashboardDto;
import com.apexinstitute.dto.ManagementDashboardDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

@Service
public class ManagementDashboardService {

    private final JdbcTemplate jdbc;

    public ManagementDashboardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public ManagementDashboardDto load() {

        // ---------- Stats ----------
        BigDecimal monthIncome = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM payments
                WHERE MONTH(payment_date) = MONTH(SYSDATETIME())
                  AND YEAR(payment_date)  = YEAR(SYSDATETIME())
                """, BigDecimal.class);

        BigDecimal monthExpense = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM teacher_salaries
                WHERE [month] = MONTH(SYSDATETIME())
                  AND [year]  = YEAR(SYSDATETIME())
                """, BigDecimal.class);

        BigDecimal outstanding = jdbc.queryForObject("""
                SELECT COALESCE(SUM(total_amount), 0) FROM invoices WHERE status = 'unpaid'
                """, BigDecimal.class);

        Integer outCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM invoices WHERE status = 'unpaid'", Integer.class);

        double inc = monthIncome == null ? 0 : monthIncome.doubleValue();
        double exp = monthExpense == null ? 0 : monthExpense.doubleValue();
        double out = outstanding == null ? 0 : outstanding.doubleValue();

        Stats stats = new Stats(round1(inc), round1(exp), round1(inc - exp),
                round1(out), outCount == null ? 0 : outCount);

        // ---------- Monthly chart (last 6 months) ----------
        List<MonthBar> monthly = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 5; i >= 0; i--) {
            LocalDateTime m = now.minusMonths(i);
            int y = m.getYear(), mo = m.getMonthValue();

            BigDecimal mi = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(amount), 0) FROM payments
                    WHERE MONTH(payment_date) = ? AND YEAR(payment_date) = ?
                    """, BigDecimal.class, mo, y);

            BigDecimal me = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(amount), 0) FROM teacher_salaries
                    WHERE [month] = ? AND [year] = ?
                    """, BigDecimal.class, mo, y);

            monthly.add(new MonthBar(
                    m.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    y,
                    round1(mi == null ? 0 : mi.doubleValue()),
                    round1(me == null ? 0 : me.doubleValue())));
        }

        // ---------- Recent transactions (income + expense mixed) ----------
        List<TransactionItem> recent = new ArrayList<>();

        jdbc.query("""
                SELECT TOP 5 p.amount, p.payment_date, p.method,
                       i.invoice_no, u.full_name AS student_name
                FROM payments p
                JOIN invoices i ON i.id = p.invoice_id
                JOIN students s ON s.id = i.student_id
                JOIN users u ON u.id = s.user_id
                ORDER BY p.created_at DESC
                """,
                rs -> {
                    while (rs.next()) {
                        recent.add(new TransactionItem(
                                "income",
                                "Payment received — " + rs.getString("invoice_no"),
                                rs.getString("student_name") + " · " + rs.getString("method"),
                                round1(rs.getBigDecimal("amount").doubleValue()),
                                relTime(rs.getTimestamp("payment_date"))));
                    }
                    return null;
                });

        jdbc.query("""
                SELECT TOP 5 ts.amount, ts.[month], ts.[year], ts.payment_date,
                       u.full_name AS teacher_name
                FROM teacher_salaries ts
                JOIN teachers t ON t.id = ts.teacher_id
                JOIN users u ON u.id = t.user_id
                ORDER BY ts.created_at DESC
                """,
                rs -> {
                    while (rs.next()) {
                        String mon = java.time.Month.of(rs.getInt("month"))
                                .getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                        recent.add(new TransactionItem(
                                "expense",
                                "Salary paid — " + rs.getString("teacher_name"),
                                mon + " " + rs.getInt("year"),
                                round1(rs.getBigDecimal("amount").doubleValue()),
                                relTime(rs.getTimestamp("payment_date"))));
                    }
                    return null;
                });

        // Sort by nothing (they're already relative); interleave income/expense for variety
        List<TransactionItem> sorted = new ArrayList<>(recent);
        sorted.sort((a, b) -> a.kind().equals("income") ? -1 : 1);
        if (sorted.size() > 6) sorted = sorted.subList(0, 6);

        return new ManagementDashboardDto(stats, monthly, sorted);
    }

    private static double round1(double n) { return Math.round(n * 10.0) / 10.0; }

    private static String relTime(Timestamp ts) {
        if (ts == null) return "—";
        Duration d = Duration.between(ts.toLocalDateTime(), LocalDateTime.now());
        long days = d.toDays();
        if (days == 0) return "Today";
        if (days == 1) return "Yesterday";
        if (days < 30) return days + "d ago";
        return ts.toLocalDateTime().format(DateTimeFormatter.ofPattern("d MMM"));
    }
}