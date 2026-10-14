package com.apexinstitute.dto;

import java.util.List;

public class StudentMaterialsDto {

    private final StudentSummary student;
    private final Stats stats;
    private final List<SubjectFilter> subjects;
    private final List<MaterialItem> materials;

    public StudentMaterialsDto(StudentSummary student,
                               Stats stats,
                               List<SubjectFilter> subjects,
                               List<MaterialItem> materials) {
        this.student = student;
        this.stats = stats;
        this.subjects = subjects;
        this.materials = materials;
    }

    public StudentSummary getStudent() { return student; }
    public Stats getStats() { return stats; }
    public List<SubjectFilter> getSubjects() { return subjects; }
    public List<MaterialItem> getMaterials() { return materials; }

    public record StudentSummary(Long id, String studentId, String fullName, String grade) {}

    public record Stats(int totalFiles, int notes, int videos) {}

    public record SubjectFilter(String subject, int count) {}

    public record MaterialItem(Long id,
                               String name,
                               String type,        // "note" | "video"
                               String subject,
                               String className,
                               String grade,
                               String uploader,
                               String uploaded,
                               String url) {}      // /uploads/notes/...
}