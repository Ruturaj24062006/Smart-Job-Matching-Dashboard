package com.careermatch.backend.job.queue;

import com.careermatch.backend.common.event.JobPostedEvent;
import com.careermatch.backend.config.QueueConfig;
import com.careermatch.backend.exception.ResourceNotFoundException;
import com.careermatch.backend.job.entity.Job;
import com.careermatch.backend.job.repository.JobRepository;
import com.careermatch.backend.matching.service.MatchingService;
import com.careermatch.backend.resume.entity.Resume;
import com.careermatch.backend.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobTaskListener {

    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final MatchingService matchingService;

    @RabbitListener(queues = QueueConfig.JOB_POSTED_QUEUE)
    public void handleJobPosted(JobPostedEvent event) {
        log.info("Received JobPostedEvent for job ID: {}", event.getJobId());
        try {
            Job job = jobRepository.findById(event.getJobId())
                    .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + event.getJobId()));

            if (job.getEmbedding() == null) {
                log.warn("Job posting has no embedding, skipping vector-based matching.");
                return;
            }

            // Retrieve only the Top 100 candidates based on pgvector similarity search
            List<Resume> topResumes = resumeRepository.searchResumesByEmbedding(job.getEmbedding(), 100);
            log.info("Found {} candidate resumes matching Job ID: {}", topResumes.size(), event.getJobId());

            for (Resume resume : topResumes) {
                try {
                    matchingService.generateMatchesForStudentAndJob(resume.getStudent().getId(), job);
                } catch (Exception e) {
                    log.error("Failed to generate match for student {} and job {}: {}", 
                            resume.getStudent().getId(), job.getId(), e.getMessage());
                }
            }
            log.info("Finished updating matches for top candidates following Job: {}", event.getJobId());
        } catch (Exception e) {
            log.error("Failed to update matches for job post asynchronously: {}", e.getMessage(), e);
            throw e; // Let exception bubble up to activate RabbitMQ retry policy and DLQ!
        }
    }
}
