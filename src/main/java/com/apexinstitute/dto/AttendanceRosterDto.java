package com.apexinstitute.dto;

import java.util.List;

public class AttendanceRosterDto {

    private final Long classId;
    private final String className;
    private final String subject;
    private final String grade;
    private final String date;
    private final boolean editing;
    private final String markedBy;
    private final String markedAt;
    private final List<StudentRow> roster;

    public AttendanceRosterDto(Long classId, String className, String subject, String grade,
                               String date, boolean editing,
                               String markedBy, String markedAt,
                               List<StudentRow> roster) {
        this.classId = classId;
        this.className = className;
        this.subject = subject;
        this.grade = grade;
        this.date = date;
        this.editing = editing;
        this.markedBy = markedBy;
        this.markedAt = markedAt;
        this.roster = roster;
    }

    public Long getClassId() { return classId; }
    public String getClassName() { return className; }
    public String getSubject() { return subject; }
    public String getGrade() { return grade; }
    public String getDate() { return date; }
    public boolean isEditing() { return editing; }
    public String getMarkedBy() { return markedBy; }
    public String getMarkedAt() { return markedAt; }
    public List<StudentRow> getRoster() { return roster; }

    public record StudentRow(Long studentId, String studentCode, String fullName,
                             String rfid, String status) {}
}