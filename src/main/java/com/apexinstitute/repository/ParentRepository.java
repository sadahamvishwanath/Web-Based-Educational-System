package com.apexinstitute.repository;

import com.apexinstitute.entity.Parent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ParentRepository extends JpaRepository<Parent, Long> {
    Optional<Parent> findByNic(String nic);
    boolean existsByNic(String nic);
}