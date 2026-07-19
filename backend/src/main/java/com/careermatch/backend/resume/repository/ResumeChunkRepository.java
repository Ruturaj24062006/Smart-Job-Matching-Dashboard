package com.careermatch.backend.resume.repository;

import com.careermatch.backend.resume.entity.ResumeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ResumeChunkRepository extends JpaRepository<ResumeChunk, UUID> {
    List<ResumeChunk> findByResumeIdOrderByChunkIndexAsc(UUID resumeId);
    void deleteByResumeId(UUID resumeId);
}
