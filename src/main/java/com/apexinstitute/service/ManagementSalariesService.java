package com.apexinstitute.service;

import com.apexinstitute.dto.ManagementSalariesDto;
import com.apexinstitute.dto.ManagementSalariesDto.SalaryItem;
import com.apexinstitute.dto.ManagementSalariesDto.Stats;
import com.apexinstitute.dto.ManagementSalariesDto.TeacherOption;
import com.apexinstitute.dto.SaveSalaryRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Service
public class ManagementSalariesService {

    private final JdbcTemplate jdbc;
    private final ManagementContext ctx;

    public ManagementSalariesService(JdbcTemplate jdbc, ManagementContext ctx) {
        this.jdbc = jdbc;
        this.ctx = ctx;
    }

    /* ---------- GET /api/management/salaries ---------- */
    public ManagementSalariesDto list() {

        List<SalaryItem> salaries = jdbc.query("""
                SELECT ts.id, ts.amount, ts.[month], ts.[year],
                       ts.payment_date, ts.method, ts.status,
                       u.full_name AS teacher_name,
                       r.full_name AS recorder_name
                FROM teacher_salaries ts
                JOIN teachers t ON t.id = ts.teacher_id
                JOIN users u ON u.id = t.user_id
                LEFT JOIN users r ON r.id = ts.recorded_by
                ORDER BY ts.[year] DESC, ts.[month] DESC, ts.id DESC
                """,
                (rs, i) -> {
                    Date pd = rs.getDate("payment_date");
                    String paidOn = pd == null ? null
                            : pd.toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy"));
                    String mon = java.time.Month.of(rs.getInt("month"))
                            .getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                    return new SalaryItem(
                            rs.getLong("id"),
                            rs.getString("teacher_name"),
                            mon,
                            rs.getInt("year"),
                            rs.getBigDecimal("amount").doubleValue(),
                            rs.getString("status"),
                            rs.getString("method"),
                            paidOn,
                            rs.getString("recorder_name"));
                });

        double paid = salaries.stream().filter(s -> "paid".equals(s.status()))
                .mapToDouble(SalaryItem::amount).sum();
        double pending = salaries.stream().filter(s -> "pending".equals(s.status()))
                .mapToDouble(SalaryItem::amount).sum();
        int paidCount = (int) salaries.stream().filter(s -> "paid".equals(s.status())).count();
        int pendingCount = salaries.size() - paidCount;

        Stats stats = new Stats(round1(paid + pending), round1(paid), round1(pending),
                paidCount, pendingCount);

        List<TeacherOption> teachers = jdbc.query("""
                SELECT t.id, u.full_name
                FROM teachers t
                JOIN users u ON u.id = t.user_id
                WHERE t.is_active = 1
                ORDER BY u.full_name
                """,
                (rs, i) -> new TeacherOption(
                        rs.getLong("id"),
                        rs.getString("full_name")));

        return new ManagementSalariesDto(stats, teachers, salaries);
    }

    /* ---------- POST /api/management/salaries ---------- */
    @Transactional
    public Long create(SaveSalaryRequest req) {
        // Unique (teacher_id, month, year)
        Integer dup = jdbc.queryForObject("""
                SELECT COUNT(*) FROM teacher_salaries
                WHERE teacher_id = ? AND [month] = ? AND [year] = ?
                """, Integer.class, req.getTeacherId(), req.getMonth(), req.getYear());
        if (dup != null && dup > 0)
            throw new RuntimeException("A salary record already exists for this teacher and month");

        ManagementContext.ManagerInfo m = ctx.current();

        String status = req.isMarkPaid() ? "paid" : "pending";
        java.sql.Date paidDate = req.isMarkPaid() ? java.sql.Date.valueOf(java.time.LocalDate.now()) : null;

        jdbc.update("""
                INSERT INTO teacher_salaries
                    (teacher_id, amount, [month], [year], payment_date, method,
                     status, recorded_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, SYSDATETIME())
                """,
                req.getTeacherId(), req.getAmount(),
                req.getMonth(), req.getYear(),
                paidDate, req.getMethod(),
                status, m.id());

        return jdbc.queryForObject(
                "SELECT TOP 1 id FROM teacher_salaries ORDER BY id DESC", Long.class);
    }

    /* ---------- PATCH /api/management/salaries/{id}/mark-paid ---------- */
    @Transactional
    public void markPaid(Long id) {
        int updated = jdbc.update("""
                UPDATE teacher_salaries
                SET status = 'paid', payment_date = CAST(SYSDATETIME() AS DATE)
                WHERE id = ? AND status = 'pending'
                """, id);
        if (updated == 0) throw new RuntimeException("Salary not found or already paid");
    }

    /* ---------- DELETE /api/management/salaries/{id} ---------- */
    @Transactional
    public void delete(Long id) {
        int deleted = jdbc.update(
                "DELETE FROM teacher_salaries WHERE id = ? AND status = 'pending'", id);
        if (deleted == 0) throw new RuntimeException("Only pending salaries can be deleted");
    }

    private static double round1(double n) { return Math.round(n * 10.0) / 10.0; }
}