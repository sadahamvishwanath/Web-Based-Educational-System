package com.apexinstitute.dto;

import java.util.List;

public class MarksRosterDto {

    private final Long examId;
    private final String title;
    private final String className;
    private final String subject;
    private final String grade;
    private final int totalMarks;
    private final boolean published;
    private final List<StudentMark> roster;

    public MarksRosterDto(Long examId, String title, String className, String subject,
                          String grade, int totalMarks, boolean published,
                          List<StudentMark> roster) {
        this.examId = examId;
        this.title = title;
        this.className = className;
        this.subject = subject;
        this.grade = grade;
        this.totalMarks = totalMarks;
        this.published = published;
        this.roster = roster;
    }

    public Long getExamId() { return examId; }
    public String getTitle() { return title; }
    public String getClassName() { return className; }
    public String getSubject() { return subject; }
    public String getGrade() { return grade; }
    public int getTotalMarks() { return totalMarks; }
    public boolean isPublished() { return published; }
    public List<StudentMark> getRoster() { return roster; }

    public record StudentMark(Long studentId, String studentCode, String fullName,
                              Double marks) {}
}