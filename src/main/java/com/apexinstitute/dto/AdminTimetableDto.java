package com.apexinstitute.dto;

import java.util.List;

public class AdminTimetableDto {

    private final List<String> days;
    private final List<Slot> slots;
    private final List<SlotEntry> entries;
    private final List<ClassOption> classes;
    private final List<TeacherOption> teachers;
    private final List<RoomOption> rooms;
    private final Stats stats;

    public AdminTimetableDto(List<String> days,
                             List<Slot> slots,
                             List<SlotEntry> entries,
                             List<ClassOption> classes,
                             List<TeacherOption> teachers,
                             List<RoomOption> rooms,
                             Stats stats) {
        this.days = days;
        this.slots = slots;
        this.entries = entries;
        this.classes = classes;
        this.teachers = teachers;
        this.rooms = rooms;
        this.stats = stats;
    }

    public List<String> getDays() { return days; }
    public List<Slot> getSlots() { return slots; }
    public List<SlotEntry> getEntries() { return entries; }
    public List<ClassOption> getClasses() { return classes; }
    public List<TeacherOption> getTeachers() { return teachers; }
    public List<RoomOption> getRooms() { return rooms; }
    public Stats getStats() { return stats; }

    public record Slot(String start, String end) {}

    public record SlotEntry(Long id,
                            Long classId, String className, String subject, String grade,
                            Long teacherId, String teacherName,
                            Long roomId, String roomName,
                            String day, String start, String end) {}

    public record ClassOption(Long id, String name, String subject, String grade) {}

    public record TeacherOption(Long id, String name) {}

    public record RoomOption(Long id, String name) {}

    public record Stats(int totalSlots, int classesUsed, int teachersUsed, int roomsUsed) {}
}