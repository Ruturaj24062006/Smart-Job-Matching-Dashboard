package com.careermatch.backend.resume.queue;

import com.careermatch.backend.common.event.ResumeUploadedEvent;
import com.careermatch.backend.config.QueueConfig;
import com.careermatch.backend.resume.service.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResumeTaskListener {

    private final ResumeService resumeService;

    @RabbitListener(queues = QueueConfig.RESUME_UPLOADED_QUEUE)
    public void handleResumeUploaded(ResumeUploadedEvent event) {
        log.info("Received ResumeUploadedEvent for resume ID: {}", event.getResumeId());
        try {
            resumeService.processResume(event.getResumeId());
        } catch (Exception e) {
            log.error("Failed to process resume event asynchronously: {}", e.getMessage());
        }
    }
}
