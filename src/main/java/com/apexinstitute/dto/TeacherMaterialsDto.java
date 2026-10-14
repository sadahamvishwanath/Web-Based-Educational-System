package com.apexinstitute.dto;

import java.util.List;

public class TeacherMaterialsDto {

    private final TeacherSummary teacher;
    private final Stats stats;
    private final List<MaterialItem> materials;

    public TeacherMaterialsDto(TeacherSummary teacher, Stats stats,
                               List<MaterialItem> materials) {
        this.teacher = teacher;
        this.stats = stats;
        this.materials = materials;
    }

    public TeacherSummary getTeacher() { return teacher; }
    public Stats getStats() { return stats; }
    public List<MaterialItem> getMaterials() { return materials; }

    public record TeacherSummary(Long id, String teacherId, String fullName) {}

    public record Stats(int totalFiles, int notes, int videos) {}

    public record MaterialItem(Long id, String name, String type,
                               String subject, String className, String grade,
                               String uploaded, String size, String url) {}
}