package com.careermatch.backend.job.queue;

import com.careermatch.backend.common.event.JobPostedEvent;
import com.careermatch.backend.config.QueueConfig;
import com.careermatch.backend.exception.ResourceNotFoundException;
import com.careermatch.backend.job.entity.Job;
import com.careermatch.backend.job.repository.JobRepository;
import com.careermatch.backend.matching.service.MatchingService;
import com.careermatch.backend.student.entity.Student;
import com.careermatch.backend.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobTaskListener {

    private final JobRepository jobRepository;
    private final StudentRepository studentRepository;
    private final MatchingService matchingService;

    @RabbitListener(queues = QueueConfig.JOB_POSTED_QUEUE)
    @EventListener
    @Async
    public void handleJobPosted(JobPostedEvent event) {
        log.info("Received JobPostedEvent (RabbitMQ or Local) for job ID: {}", event.getJobId());
        try {
            Job job = jobRepository.findById(event.getJobId())
                    .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + event.getJobId()));

            // Retrieve all students to calculate match scores in real-time
            List<Student> allStudents = studentRepository.findAll();
            log.info("Found {} total students to calculate matches for Job ID: {}", allStudents.size(), event.getJobId());

            for (Student student : allStudents) {
                try {
                    matchingService.generateMatchForStudentAndJob(student, job);
                } catch (Exception e) {
                    log.error("Failed to generate match for student {} and job {}: {}", 
                            student.getId(), job.getId(), e.getMessage());
                }
            }
            log.info("Finished updating matches for all students following Job: {}", event.getJobId());
        } catch (Exception e) {
            log.error("Failed to update matches for job post asynchronously: {}", e.getMessage(), e);
            throw e; // Let exception bubble up to activate RabbitMQ retry policy and DLQ!
        }
    }
}
