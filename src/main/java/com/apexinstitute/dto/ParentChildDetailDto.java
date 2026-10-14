package com.apexinstitute.dto;

import java.util.List;

public class ParentChildDetailDto {

    private final ChildSummary child;
    private final List<String> days;
    private final List<Slot> slots;
    private final List<TimetableEntry> timetable;
    private final AttendanceBlock attendance;
    private final List<ExamRow> exams;

    public ParentChildDetailDto(ChildSummary child,
                                List<String> days, List<Slot> slots,
                                List<TimetableEntry> timetable,
                                AttendanceBlock attendance,
                                List<ExamRow> exams) {
        this.child = child;
        this.days = days;
        this.slots = slots;
        this.timetable = timetable;
        this.attendance = attendance;
        this.exams = exams;
    }

    public ChildSummary getChild() { return child; }
    public List<String> getDays() { return days; }
    public List<Slot> getSlots() { return slots; }
    public List<TimetableEntry> getTimetable() { return timetable; }
    public AttendanceBlock getAttendance() { return attendance; }
    public List<ExamRow> getExams() { return exams; }

    public record ChildSummary(Long id, String studentCode, String fullName,
                               String grade, String email) {}

    public record Slot(String start, String end) {}

    public record TimetableEntry(String day, String start, String end,
                                 String subject, String teacher, String room) {}

    public record AttendanceBlock(int total, int present, int absent, int late,
                                  double pct, List<SubjectAttendance> bySubject) {}

    public record SubjectAttendance(String subject, int total, int present,
                                    int absent, int late, double pct) {}

    public record ExamRow(Long id, String subject, String title, String teacher,
                          String date, double marks, int totalMarks,
                          double percentage, String grade) {}
}