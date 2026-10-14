package com.apexinstitute.service;

import com.apexinstitute.dto.ManagementFeesDto;
import com.apexinstitute.dto.ManagementFeesDto.FeeItem;
import com.apexinstitute.dto.ManagementFeesDto.GradeGroup;
import com.apexinstitute.dto.SaveFeeRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ManagementFeesService {

    private final JdbcTemplate jdbc;

    public ManagementFeesService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /* ---------- GET /api/management/fees ---------- */
    public ManagementFeesDto list() {
        List<GradeGroup> grades = new ArrayList<>();
        for (String g : List.of("10", "11", "12", "13")) {
            List<FeeItem> items = jdbc.query("""
                    SELECT id, fee_type, amount FROM fee_structures
                    WHERE grade = ? AND is_active = 1
                    ORDER BY fee_type
                    """,
                    (rs, i) -> new FeeItem(
                            rs.getLong("id"),
                            rs.getString("fee_type"),
                            rs.getBigDecimal("amount").doubleValue()),
                    g);

            double total = items.stream().mapToDouble(FeeItem::amount).sum();
            grades.add(new GradeGroup(g, round1(total), items));
        }
        return new ManagementFeesDto(grades);
    }

    /* ---------- POST /api/management/fees ---------- */
    @Transactional
    public Long create(SaveFeeRequest req) {
        // Unique per (grade, fee_type)
        Integer dup = jdbc.queryForObject("""
                SELECT COUNT(*) FROM fee_structures
                WHERE grade = ? AND LOWER(fee_type) = LOWER(?)
                """, Integer.class, req.getGrade(), req.getFeeType().trim());
        if (dup != null && dup > 0)
            throw new RuntimeException("This fee already exists for Grade " + req.getGrade());

        jdbc.update("""
                INSERT INTO fee_structures (grade, fee_type, amount, is_active, created_at)
                VALUES (?, ?, ?, 1, SYSDATETIME())
                """, req.getGrade(), req.getFeeType().trim(), req.getAmount());

        return jdbc.queryForObject(
                "SELECT TOP 1 id FROM fee_structures ORDER BY id DESC", Long.class);
    }

    /* ---------- PUT /api/management/fees/{id} ---------- */
    @Transactional
    public void update(Long id, SaveFeeRequest req) {
        int updated = jdbc.update("""
                UPDATE fee_structures
                SET fee_type = ?, amount = ?
                WHERE id = ?
                """, req.getFeeType().trim(), req.getAmount(), id);
        if (updated == 0) throw new RuntimeException("Fee not found");
    }

    /* ---------- DELETE /api/management/fees/{id} ---------- */
    @Transactional
    public void delete(Long id) {
        int deleted = jdbc.update("DELETE FROM fee_structures WHERE id = ?", id);
        if (deleted == 0) throw new RuntimeException("Fee not found");
    }

    private static double round1(double n) { return Math.round(n * 10.0) / 10.0; }
}