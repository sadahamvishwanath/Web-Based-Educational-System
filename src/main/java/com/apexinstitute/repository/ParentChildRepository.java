package com.apexinstitute.repository;

import com.apexinstitute.entity.ParentChild;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentChildRepository extends JpaRepository<ParentChild, Long> {
    boolean existsByParentIdAndStudentId(Long parentId, Long studentId);
}