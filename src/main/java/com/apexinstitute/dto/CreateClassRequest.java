package com.apexinstitute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateClassRequest {

    @NotBlank @Size(max = 100)
    private String name;

    @NotBlank @Pattern(regexp = "10|11|12|13")
    private String grade;

    @NotNull
    private Long subjectId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
}