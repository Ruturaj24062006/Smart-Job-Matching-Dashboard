package com.careermatch.backend.job.queue;

import com.careermatch.backend.common.event.JobPostedEvent;
import com.careermatch.backend.config.QueueConfig;
import com.careermatch.backend.student.entity.Student;
import com.careermatch.backend.student.repository.StudentRepository;
import com.careermatch.backend.matching.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobTaskListener {

    private final StudentRepository studentRepository;
    private final MatchingService matchingService;

    @RabbitListener(queues = QueueConfig.JOB_POSTED_QUEUE)
    public void handleJobPosted(JobPostedEvent event) {
        log.info("Received JobPostedEvent for job ID: {}", event.getJobId());
        try {
            List<Student> students = studentRepository.findAll();
            for (Student student : students) {
                matchingService.generateMatchesForStudent(student.getId());
            }
            log.info("Finished updating matches for all students following Job: {}", event.getJobId());
        } catch (Exception e) {
            log.error("Failed to update matches for job post asynchronously: {}", e.getMessage());
        }
    }
}
