package com.apexinstitute.dto;

import java.util.List;

public class ParentChildrenDto {

    private final ParentSummary parent;
    private final List<ChildFull> children;

    public ParentChildrenDto(ParentSummary parent, List<ChildFull> children) {
        this.parent = parent;
        this.children = children;
    }

    public ParentSummary getParent() { return parent; }
    public List<ChildFull> getChildren() { return children; }

    public record ParentSummary(Long id, String nic, String fullName) {}

    public record ChildFull(Long id,
                            String studentCode, String fullName, String email,
                            String grade, String rfidTag, String joined,
                            double attendance, int attendanceTotal, int attendancePresent,
                            int classesCount, List<String> subjects,
                            String latestGrade, double latestPct) {}
}