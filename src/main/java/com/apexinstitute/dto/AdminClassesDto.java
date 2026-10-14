package com.apexinstitute.dto;

import java.util.List;

public class AdminClassesDto {

    private final Stats stats;
    private final List<ClassItem> classes;
    private final List<SubjectOption> subjects;

    public AdminClassesDto(Stats stats, List<ClassItem> classes,
                           List<SubjectOption> subjects) {
        this.stats = stats;
        this.classes = classes;
        this.subjects = subjects;
    }

    public Stats getStats() { return stats; }
    public List<ClassItem> getClasses() { return classes; }
    public List<SubjectOption> getSubjects() { return subjects; }

    public record Stats(int total, int active, int inactive, int students) {}

    public record ClassItem(Long id, String name, String subject, String grade,
                            int students, int timetableSlots, boolean isActive) {}

    public record SubjectOption(Long id, String name) {}
}