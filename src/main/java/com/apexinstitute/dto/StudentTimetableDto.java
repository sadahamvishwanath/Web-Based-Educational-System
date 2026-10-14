package com.apexinstitute.dto;

import java.util.List;

public class StudentTimetableDto {

    private final StudentSummary student;
    private final List<String> days;
    private final List<Slot> slots;
    private final List<Entry> entries;

    public StudentTimetableDto(StudentSummary student,
                               List<String> days,
                               List<Slot> slots,
                               List<Entry> entries) {
        this.student = student;
        this.days = days;
        this.slots = slots;
        this.entries = entries;
    }

    public StudentSummary getStudent() { return student; }
    public List<String> getDays() { return days; }
    public List<Slot> getSlots() { return slots; }
    public List<Entry> getEntries() { return entries; }

    public record StudentSummary(Long id, String studentId, String fullName, String grade) {}

    public record Slot(String start, String end) {}

    public record Entry(String day, String start, String end,
                        Long classId, String className, String grade,
                        String subject, String teacher, String room) {}
}