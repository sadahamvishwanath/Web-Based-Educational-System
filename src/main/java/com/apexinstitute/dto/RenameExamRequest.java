package com.apexinstitute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RenameExamRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be 200 characters or fewer")
    private String title;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}