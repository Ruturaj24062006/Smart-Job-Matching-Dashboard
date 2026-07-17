package com.careermatch.backend.resume.repository;

import com.careermatch.backend.resume.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, UUID> {
    List<Resume> findByStudentId(UUID studentId);
    Optional<Resume> findByStudentIdAndIsCurrentTrue(UUID studentId);
}
