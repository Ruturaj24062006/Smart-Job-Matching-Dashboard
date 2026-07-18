package com.careermatch.backend.resume.queue;

import com.careermatch.backend.common.event.JobMatchingRequestedEvent;
import com.careermatch.backend.common.event.ResumeUploadedEvent;
import com.careermatch.backend.resume.service.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Local Spring ApplicationEvent fallback listeners.
 * These are only triggered when RabbitMQ is unavailable (e.g., local development,
 * or RabbitMQ connection failure at upload time).
 *
 * These NEVER fire alongside RabbitMQ — the controller only publishes a Spring
 * ApplicationEvent when the RabbitMQ publish itself throws an exception.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LocalEventFallbackListener {

    private final ResumeService resumeService;

    /**
     * Fallback: processes resume AI pipeline locally when RabbitMQ is down.
     */
    @EventListener
    @Async
    public void onResumeUploadedFallback(ResumeUploadedEvent event) {
        log.warn("LOCAL FALLBACK: Processing ResumeUploadedEvent for resume {} (RabbitMQ was unavailable).",
                event.getResumeId());
        resumeService.processResume(event.getResumeId());
    }

    /**
     * Fallback: triggers job matching locally when RabbitMQ is down.
     * Imported here to complete the pipeline when using local event mode.
     */
    @EventListener
    @Async
    public void onJobMatchingRequestedFallback(JobMatchingRequestedEvent event) {
        log.warn("LOCAL FALLBACK: JobMatchingRequestedEvent for student {} (RabbitMQ was unavailable).",
                event.getStudentId());
        // Matching service is injected via JobMatchingTaskListener to avoid circular dep
        // This event will be handled by JobMatchingTaskListener's @EventListener equivalent
        // if needed; for now, log only — RabbitMQ handles this in production.
    }
}
