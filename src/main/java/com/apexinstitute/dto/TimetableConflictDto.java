package com.apexinstitute.dto;

import java.util.List;

public class TimetableConflictDto {

    private final boolean ok;
    private final List<Conflict> conflicts;

    public TimetableConflictDto(List<Conflict> conflicts) {
        this.conflicts = conflicts;
        this.ok = conflicts.isEmpty();
    }

    public boolean isOk() { return ok; }
    public List<Conflict> getConflicts() { return conflicts; }

    public record Conflict(String type, String message) {}
}