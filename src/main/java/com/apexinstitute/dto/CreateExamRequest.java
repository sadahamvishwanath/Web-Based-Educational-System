package com.apexinstitute.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateExamRequest {

    @NotNull
    private Long classId;

    @NotBlank
    private String title;

    @NotNull @Min(1)
    private Integer totalMarks;

    private boolean publish;

    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getTotalMarks() { return totalMarks; }
    public void setTotalMarks(Integer totalMarks) { this.totalMarks = totalMarks; }
    public boolean isPublish() { return publish; }
    public void setPublish(boolean publish) { this.publish = publish; }
}