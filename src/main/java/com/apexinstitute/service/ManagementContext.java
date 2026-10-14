package com.apexinstitute.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ManagementContext {

    private final JdbcTemplate jdbc;

    public ManagementContext(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public ManagerInfo current() {
        Long userId = Long.parseLong(
                SecurityContextHolder.getContext().getAuthentication().getName());

        ManagerInfo m = jdbc.queryForObject("""
                SELECT id, full_name FROM users WHERE id = ?
                """,
                (rs, i) -> new ManagerInfo(rs.getLong("id"), rs.getString("full_name")),
                userId);

        if (m == null) throw new RuntimeException("User not found");
        return m;
    }

    public record ManagerInfo(Long id, String fullName) {}
}