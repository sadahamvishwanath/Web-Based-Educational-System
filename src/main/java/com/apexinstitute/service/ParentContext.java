package com.apexinstitute.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ParentContext {

    private final JdbcTemplate jdbc;

    public ParentContext(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public ParentInfo current() {
        Long userId = Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getName());

        ParentInfo p = jdbc.queryForObject("""
                SELECT p.id, p.nic, u.full_name
                FROM parents p
                JOIN users u ON u.id = p.user_id
                WHERE p.user_id = ?
                """,
                (rs, i) -> new ParentInfo(
                        rs.getLong("id"),
                        rs.getString("nic"),
                        rs.getString("full_name")),
                userId);

        if (p == null) throw new RuntimeException("Parent record not found");
        return p;
    }

    /** Verify parent owns this child; throw 400 if not. */
    public void assertChild(Long parentId, Long childId) {
        Integer owns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM parent_child
                WHERE parent_id = ? AND student_id = ?
                """, Integer.class, parentId, childId);

        if (owns == null || owns == 0)
            throw new RuntimeException("You do not have access to this student");
    }

    public List<Long> childIds(Long parentId) {
        return jdbc.queryForList("""
                SELECT student_id FROM parent_child WHERE parent_id = ?
                """, Long.class, parentId);
    }

    public record ParentInfo(Long id, String nic, String fullName) {}
}