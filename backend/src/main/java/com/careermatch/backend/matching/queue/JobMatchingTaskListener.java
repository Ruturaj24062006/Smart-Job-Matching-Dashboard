package com.careermatch.backend.matching.queue;

import com.careermatch.backend.common.event.JobMatchingRequestedEvent;
import com.careermatch.backend.config.QueueConfig;
import com.careermatch.backend.matching.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes JobMatchingRequestedEvent from the dedicated job.matching.queue.
 *
 * This listener is the entry point for the full hybrid RAG matching pipeline:
 *   pgvector cosine search (top-100) + BM25 FTS (top-100)
 *   → Reciprocal Rank Fusion (k=60)
 *   → Deterministic ScoringService (6-factor weighted composite score)
 *   → Sorted descending and persisted to Match table
 *   → Cached in Redis with 1-hour TTL
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobMatchingTaskListener {

    private final MatchingService matchingService;

    @RabbitListener(queues = QueueConfig.JOB_MATCHING_QUEUE)
    public void handleJobMatchingRequested(JobMatchingRequestedEvent event) {
        log.info("RabbitMQ: Received JobMatchingRequestedEvent for student ID: {}", event.getStudentId());
        try {
            var matches = matchingService.generateMatchesForStudent(event.getStudentId());
            log.info("Job matching pipeline complete. Generated {} matches for student {}.",
                    matches.size(), event.getStudentId());
        } catch (Exception e) {
            log.error("Job matching pipeline failed for student {}: {}", event.getStudentId(), e.getMessage(), e);
            // Re-throw to trigger RabbitMQ retry → DLQ after max-attempts
            throw e;
        }
    }
}
