package com.careermatch.backend.matching.repository;

import com.careermatch.backend.matching.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchRepository extends JpaRepository<Match, UUID> {
    List<Match> findByStudentIdOrderByCompositeScoreDesc(UUID studentId);
    Optional<Match> findByStudentIdAndJobId(UUID studentId, UUID jobId);
    List<Match> findByJobIdOrderByCompositeScoreDesc(UUID jobId);
}
