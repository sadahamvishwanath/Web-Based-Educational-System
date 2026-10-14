package com.apexinstitute.service;

import com.apexinstitute.dto.AdminRoomsDto;
import com.apexinstitute.dto.AdminRoomsDto.RoomItem;
import com.apexinstitute.dto.CreateRoomRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminRoomsService {

    private final JdbcTemplate jdbc;

    public AdminRoomsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /* ---------- GET /api/admin/rooms ---------- */
    public AdminRoomsDto list() {

        List<RoomItem> rooms = jdbc.query("""
                SELECT r.id, r.name,
                       (SELECT COUNT(*) FROM timetable t WHERE t.room_id = r.id) AS usage_count
                FROM rooms r
                ORDER BY r.name
                """,
                (rs, i) -> new RoomItem(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getInt("usage_count")));

        int inUse = (int) rooms.stream().filter(r -> r.usageCount() > 0).count();
        return new AdminRoomsDto(rooms, rooms.size(), inUse);
    }

    /* ---------- POST /api/admin/rooms ---------- */
    @Transactional
    public Long create(CreateRoomRequest req) {
        String name = req.getName().trim();

        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(*) FROM rooms WHERE LOWER(name) = LOWER(?)
                """, Integer.class, name);
        if (exists != null && exists > 0)
            throw new RuntimeException("Room already exists");

        jdbc.update("INSERT INTO rooms (name) VALUES (?)", name);

        return jdbc.queryForObject(
                "SELECT TOP 1 id FROM rooms ORDER BY id DESC", Long.class);
    }

    /* ---------- DELETE /api/admin/rooms/{id} ---------- */
    @Transactional
    public void delete(Long id) {
        Integer used = jdbc.queryForObject("""
                SELECT COUNT(*) FROM timetable WHERE room_id = ?
                """, Integer.class, id);
        if (used != null && used > 0)
            throw new RuntimeException(
                    "Cannot delete — used in " + used + " timetable slot"
                            + (used == 1 ? "" : "s"));

        int deleted = jdbc.update("DELETE FROM rooms WHERE id = ?", id);
        if (deleted == 0) throw new RuntimeException("Room not found");
    }
}