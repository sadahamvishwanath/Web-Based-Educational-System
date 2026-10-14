package com.apexinstitute.dto;

import java.util.List;

public class AdminSubjectsDto {

    private final List<SubjectItem> subjects;
    private final int total;
    private final int inUse;

    public AdminSubjectsDto(List<SubjectItem> subjects, int total, int inUse) {
        this.subjects = subjects;
        this.total = total;
        this.inUse = inUse;
    }

    public List<SubjectItem> getSubjects() { return subjects; }
    public int getTotal() { return total; }
    public int getInUse() { return inUse; }

    public record SubjectItem(Long id, String name, int classCount, int gradeCount) {}
}