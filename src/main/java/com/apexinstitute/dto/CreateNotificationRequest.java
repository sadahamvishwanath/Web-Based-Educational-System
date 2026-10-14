package com.apexinstitute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateNotificationRequest {

    @NotBlank @Size(max = 255)
    private String title;

    @NotBlank
    private String message;

    @NotBlank @Pattern(regexp = "general|students|parents|teachers|class")
    private String audience;

    /** Required when audience = 'class'; null otherwise. */
    private Long classId;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
}