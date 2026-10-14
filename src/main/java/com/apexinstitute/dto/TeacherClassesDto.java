package com.apexinstitute.dto;

import java.util.List;

public class TeacherClassesDto {

    private final TeacherSummary teacher;
    private final List<ClassItem> classes;

    public TeacherClassesDto(TeacherSummary teacher, List<ClassItem> classes) {
        this.teacher = teacher;
        this.classes = classes;
    }

    public TeacherSummary getTeacher() { return teacher; }
    public List<ClassItem> getClasses() { return classes; }

    public record TeacherSummary(Long id, String teacherId, String fullName) {}

    public record ClassItem(Long id, String name, String subject, String grade,
                            String day, String time, String room,
                            int students, double attendanceRate) {}
}