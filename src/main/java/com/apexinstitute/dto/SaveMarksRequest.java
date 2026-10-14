package com.apexinstitute.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public class SaveMarksRequest {

    @NotNull
    private List<Entry> entries;

    public List<Entry> getEntries() { return entries; }
    public void setEntries(List<Entry> entries) { this.entries = entries; }

    public static class Entry {
        @NotNull private Long studentId;
        private Double marks;   // null = clear the mark

        public Long getStudentId() { return studentId; }
        public void setStudentId(Long studentId) { this.studentId = studentId; }
        public Double getMarks() { return marks; }
        public void setMarks(Double marks) { this.marks = marks; }
    }
}