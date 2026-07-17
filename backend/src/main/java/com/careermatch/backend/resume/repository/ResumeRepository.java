package com.careermatch.backend.resume.repository;

import com.careermatch.backend.resume.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, UUID> {
    List<Resume> findByStudentId(UUID studentId);
    Optional<Resume> findByStudentIdAndIsCurrentTrue(UUID studentId);

    @Query(value = """
        WITH vector_search AS (
          SELECT id, student_id, ROW_NUMBER() OVER (ORDER BY embedding <=> cast(:queryVector as vector)) as rank
          FROM resumes
          WHERE is_current = true AND embedding IS NOT NULL
          ORDER BY embedding <=> cast(:queryVector as vector)
          LIMIT 100
        )
        SELECT r.*
        FROM vector_search v
        JOIN resumes r ON r.id = v.id
        ORDER BY v.rank ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<Resume> searchResumesByEmbedding(
        @Param("queryVector") float[] queryVector,
        @Param("limit") int limit
    );
}
