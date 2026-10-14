package com.apexinstitute.service;

import com.apexinstitute.dto.AdminTimetableDto;
import com.apexinstitute.dto.AdminTimetableDto.*;
import com.apexinstitute.dto.TimetableConflictDto;
import com.apexinstitute.dto.TimetableConflictDto.Conflict;
import com.apexinstitute.dto.TimetableSlotRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AdminTimetableService {

    private final JdbcTemplate jdbc;

    public AdminTimetableService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final List<Slot> FIXED_SLOTS = List.of(
            new Slot("08:00", "10:00"),
            new Slot("10:00", "12:00"),
            new Slot("13:00", "15:00"),
            new Slot("15:00", "17:00"));

    private static final List<String> DAYS = List.of(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday");

    /* ---------- GET /api/admin/timetable ---------- */
    public AdminTimetableDto loadGrid() {
        List<SlotEntry> entries = jdbc.query("""
                SELECT t.id, t.day_of_week, t.start_time, t.end_time,
                       c.id AS class_id, c.name AS class_name, c.grade,
                       s.name AS subject,
                       te.id AS teacher_id, u.full_name AS teacher_name,
                       r.id AS room_id, r.name AS room_name
                FROM timetable t
                JOIN classes  c ON c.id = t.class_id
                JOIN subjects s ON s.id = c.subject_id
                JOIN teachers te ON te.id = t.teacher_id
                JOIN users u ON u.id = te.user_id
                JOIN rooms r ON r.id = t.room_id
                ORDER BY t.start_time
                """,
                (rs, i) -> new SlotEntry(
                        rs.getLong("id"),
                        rs.getLong("class_id"),
                        rs.getString("class_name"),
                        rs.getString("subject"),
                        rs.getString("grade"),
                        rs.getLong("teacher_id"),
                        rs.getString("teacher_name"),
                        rs.getLong("room_id"),
                        rs.getString("room_name"),
                        rs.getString("day_of_week"),
                        trim(rs.getString("start_time")),
                        trim(rs.getString("end_time"))));

        List<ClassOption> classes = jdbc.query("""
                SELECT c.id, c.name, c.grade, s.name AS subject
                FROM classes c
                JOIN subjects s ON s.id = c.subject_id
                WHERE c.is_active = 1
                ORDER BY c.grade, s.name
                """,
                (rs, i) -> new ClassOption(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("subject"),
                        rs.getString("grade")));

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

        List<RoomOption> rooms = jdbc.query("""
                SELECT id, name FROM rooms ORDER BY name
                """,
                (rs, i) -> new RoomOption(
                        rs.getLong("id"),
                        rs.getString("name")));

        long distinctClasses  = entries.stream().map(SlotEntry::classId).distinct().count();
        long distinctTeachers = entries.stream().map(SlotEntry::teacherId).distinct().count();
        long distinctRooms    = entries.stream().map(SlotEntry::roomId).distinct().count();

        Stats stats = new Stats(
                entries.size(), (int) distinctClasses, (int) distinctTeachers, (int) distinctRooms);

        return new AdminTimetableDto(DAYS, FIXED_SLOTS, entries, classes, teachers, rooms, stats);
    }

    /* ---------- POST /api/admin/timetable/check ---------- */
    public TimetableConflictDto check(TimetableSlotRequest req) {
        return new TimetableConflictDto(findConflicts(req));
    }

    /* ---------- POST /api/admin/timetable ---------- */
    @Transactional
    public Long create(TimetableSlotRequest req) {
        List<Conflict> conflicts = findConflicts(req);
        if (!conflicts.isEmpty()) throw new RuntimeException(conflicts.get(0).message());

        jdbc.update("""
                INSERT INTO timetable
                    (class_id, teacher_id, room_id, day_of_week, start_time, end_time,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, CONVERT(TIME, ?), CONVERT(TIME, ?), SYSDATETIME(), SYSDATETIME())
                """,
                req.getClassId(), req.getTeacherId(), req.getRoomId(),
                req.getDay(), req.getStart(), req.getEnd());

        return jdbc.queryForObject(
                "SELECT TOP 1 id FROM timetable ORDER BY id DESC", Long.class);
    }

    /* ---------- PUT /api/admin/timetable/{id} ---------- */
    @Transactional
    public void update(Long id, TimetableSlotRequest req) {
        req.setExcludeId(id);
        List<Conflict> conflicts = findConflicts(req);
        if (!conflicts.isEmpty()) throw new RuntimeException(conflicts.get(0).message());

        int updated = jdbc.update("""
                UPDATE timetable
                SET class_id = ?, teacher_id = ?, room_id = ?,
                    day_of_week = ?, start_time = CONVERT(TIME, ?), end_time = CONVERT(TIME, ?),
                    updated_at = SYSDATETIME()
                WHERE id = ?
                """,
                req.getClassId(), req.getTeacherId(), req.getRoomId(),
                req.getDay(), req.getStart(), req.getEnd(), id);

        if (updated == 0) throw new RuntimeException("Slot not found");
    }

    /* ---------- DELETE /api/admin/timetable/{id} ---------- */
    @Transactional
    public void delete(Long id) {
        int deleted = jdbc.update("DELETE FROM timetable WHERE id = ?", id);
        if (deleted == 0) throw new RuntimeException("Slot not found");
    }

    /* ---------- Conflict engine ---------- */
    private List<Conflict> findConflicts(TimetableSlotRequest req) {
        List<Conflict> out = new ArrayList<>();
        Long excludeId = req.getExcludeId();

        // Compare TIME column to a string via CONVERT — avoids the driver's DATETIME issue
        List<SlotEntry> sameCell = jdbc.query("""
                SELECT t.id, t.day_of_week, t.start_time, t.end_time,
                       c.id AS class_id, c.name AS class_name, c.grade,
                       s.name AS subject,
                       te.id AS teacher_id, u.full_name AS teacher_name,
                       r.id AS room_id, r.name AS room_name
                FROM timetable t
                JOIN classes  c ON c.id = t.class_id
                JOIN subjects s ON s.id = c.subject_id
                JOIN teachers te ON te.id = t.teacher_id
                JOIN users u ON u.id = te.user_id
                JOIN rooms r ON r.id = t.room_id
                WHERE t.day_of_week = ?
                  AND CONVERT(VARCHAR(5), t.start_time, 108) = ?
                """,
                (rs, i) -> new SlotEntry(
                        rs.getLong("id"),
                        rs.getLong("class_id"), rs.getString("class_name"),
                        rs.getString("subject"), rs.getString("grade"),
                        rs.getLong("teacher_id"), rs.getString("teacher_name"),
                        rs.getLong("room_id"), rs.getString("room_name"),
                        rs.getString("day_of_week"),
                        trim(rs.getString("start_time")),
                        trim(rs.getString("end_time"))),
                req.getDay(),
                req.getStart());

        for (SlotEntry e : sameCell) {
            if (excludeId != null && e.id().equals(excludeId)) continue;

            if (e.teacherId().equals(req.getTeacherId())) {
                out.add(new Conflict("teacher",
                        "Teacher " + e.teacherName()
                                + " is already teaching " + e.className()
                                + " in this slot."));
            }
            if (e.roomId().equals(req.getRoomId())) {
                out.add(new Conflict("room",
                        "Room " + e.roomName()
                                + " is already booked for " + e.className()
                                + " in this slot."));
            }
            if (e.classId().equals(req.getClassId())) {
                out.add(new Conflict("class",
                        e.className()
                                + " already has a session in this slot."));
            }
        }

        out.sort(Comparator.comparing(Conflict::type));
        return out;
    }

    private static String trim(String t) {
        if (t == null) return "";
        return t.length() >= 5 ? t.substring(0, 5) : t;
    }
}