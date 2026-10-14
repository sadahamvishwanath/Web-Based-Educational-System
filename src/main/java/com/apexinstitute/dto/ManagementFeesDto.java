package com.apexinstitute.dto;

import java.util.List;

public class ManagementFeesDto {

    private final List<GradeGroup> grades;

    public ManagementFeesDto(List<GradeGroup> grades) { this.grades = grades; }
    public List<GradeGroup> getGrades() { return grades; }

    public record GradeGroup(String grade, double annualTotal, List<FeeItem> fees) {}
    public record FeeItem(Long id, String feeType, double amount) {}
}