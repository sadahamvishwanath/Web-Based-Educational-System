package com.apexinstitute.service;

import com.apexinstitute.dto.TeacherSalaryDto;
import com.apexinstitute.dto.TeacherSalaryDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
public class TeacherSalaryService {

    private final JdbcTemplate jdbc;

    public TeacherSalaryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public TeacherSalaryDto forCurrentTeacher() {

        Long userId = Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getName());

        // ---------- Teacher ----------
        TeacherSummary teacher = jdbc.queryForObject("""
                SELECT t.id, t.teacher_id, u.full_name
                FROM teachers t
                JOIN users u ON u.id = t.user_id
                WHERE t.user_id = ?
                """,
                (rs, i) -> new TeacherSummary(
                        rs.getLong("id"),
                        rs.getString("teacher_id"),
                        rs.getString("full_name")),
                userId);

        if (teacher == null) throw new RuntimeException("Teacher record not found");
        Long teacherId = teacher.id();

        // ---------- Full history ----------
        List<SalaryItem> history = jdbc.query("""
                SELECT id, amount, [month], [year], payment_date, method, status
                FROM teacher_salaries
                WHERE teacher_id = ?
                ORDER BY [year] DESC, [month] DESC
                """,
                (rs, i) -> {
                    BigDecimal amt = rs.getBigDecimal("amount");
                    Date pd = rs.getDate("payment_date");
                    String paidOn = pd == null ? null
                            : pd.toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy"));
                    int monthNum = rs.getInt("month");
                    return new SalaryItem(
                            rs.getLong("id"),
                            monthName(monthNum),
                            rs.getInt("year"),
                            amt == null ? 0.0 : amt.doubleValue(),
                            rs.getString("status"),
                            rs.getString("method"),
                            paidOn,
                            "SAL-" + rs.getInt("year") + "-" + String.format("%02d", monthNum)
                                    + "-" + String.format("%04d", rs.getLong("id")));
                },
                teacherId);

        // ---------- Stats ----------
        double ytd = 0;
        int paidCount = 0;
        double pendingAmount = 0;
        int pendingCount = 0;
        int currentYear = java.time.LocalDate.now().getYear();

        for (SalaryItem s : history) {
            if (s.year() == currentYear && "paid".equals(s.status())) {
                ytd += s.amount();
                paidCount++;
            }
            if ("pending".equals(s.status())) {
                pendingAmount += s.amount();
                pendingCount++;
            }
        }

        // Monthly rate = most recent paid amount (or 0 if none)
        double monthlyRate = history.stream()
                .filter(s -> "paid".equals(s.status()))
                .findFirst()
                .map(SalaryItem::amount)
                .orElse(0.0);

        Stats stats = new Stats(ytd, paidCount, pendingCount, pendingAmount, monthlyRate);

        // ---------- Chart: last 6 months (chronological order) ----------
        List<SalaryItem> last6 = new ArrayList<>(history);
        if (last6.size() > 6) last6 = last6.subList(0, 6);
        Collections.reverse(last6);

        List<MonthBar> chart = new ArrayList<>();
        for (SalaryItem s : last6) {
            chart.add(new MonthBar(
                    s.month().substring(0, 3),
                    s.year(),
                    s.amount(),
                    s.status()));
        }

        return new TeacherSalaryDto(teacher, stats, history, chart);
    }

    private static String monthName(int m) {
        return java.time.Month.of(m).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }
}