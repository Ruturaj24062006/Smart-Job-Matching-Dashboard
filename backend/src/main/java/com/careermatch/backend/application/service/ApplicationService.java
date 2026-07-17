package com.careermatch.backend.application.service;

import com.careermatch.backend.application.dto.ApplyRequest;
import com.careermatch.backend.application.dto.UpdateStatusRequest;
import com.careermatch.backend.application.entity.Application;
import com.careermatch.backend.application.entity.ApplicationStatus;
import com.careermatch.backend.application.repository.ApplicationRepository;
import com.careermatch.backend.auth.entity.User;
import com.careermatch.backend.auth.repository.UserRepository;
import com.careermatch.backend.exception.BadRequestException;
import com.careermatch.backend.exception.ResourceNotFoundException;
import com.careermatch.backend.job.entity.Job;
import com.careermatch.backend.job.repository.JobRepository;
import com.careermatch.backend.notification.entity.Notification;
import com.careermatch.backend.notification.entity.NotificationStatus;
import com.careermatch.backend.notification.entity.NotificationType;
import com.careermatch.backend.notification.repository.NotificationRepository;
import com.careermatch.backend.resume.entity.Resume;
import com.careermatch.backend.resume.repository.ResumeRepository;
import com.careermatch.backend.student.entity.Student;
import com.careermatch.backend.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final StudentRepository studentRepository;
    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    public Application applyToJob(UUID jobId, ApplyRequest request, String studentEmail) {
        User user = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new BadRequestException("Student user not found"));

        Student student = studentRepository.findById(user.getId())
                .orElseThrow(() -> new BadRequestException("Student profile not initialized"));

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));

        Resume resume = resumeRepository.findByStudentIdAndIsCurrentTrue(student.getId())
                .orElseThrow(() -> new BadRequestException("Please upload a resume first before applying"));

        if (applicationRepository.findByStudentIdAndJobId(student.getId(), jobId).isPresent()) {
            throw new BadRequestException("You have already applied for this job");
        }

        Application application = Application.builder()
                .student(student)
                .job(job)
                .resume(resume)
                .coverLetter(request.getCoverLetter())
                .status(ApplicationStatus.APPLIED)
                .build();

        Application saved = applicationRepository.save(application);
        log.info("Student {} applied to Job: {}", student.getId(), jobId);

        // Send a notification to the recruiter or log it
        createNotification(job.getRecruiter().getUser(), "New Application", 
                "Student " + student.getFirstName() + " has applied for your job post: " + job.getTitle());

        return saved;
    }

    @Transactional
    public Application updateApplicationStatus(UUID applicationId, UpdateStatusRequest request) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));

        application.setStatus(request.getStatus());
        application.setFeedback(request.getFeedback());
        Application saved = applicationRepository.save(application);

        log.info("Application {} status updated to {}", applicationId, request.getStatus());

        // Send a notification to the student
        String msg = "Your application for the position of " + application.getJob().getTitle() + 
                " has been updated to: " + request.getStatus();
        if (request.getFeedback() != null && !request.getFeedback().isBlank()) {
            msg += ". Feedback: " + request.getFeedback();
        }
        createNotification(application.getStudent().getUser(), "Application Status Updated", msg);

        return saved;
    }

    public List<Application> getApplicationsForStudent(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));
        return applicationRepository.findByStudentId(user.getId());
    }

    public List<Application> getApplicationsForJob(UUID jobId) {
        return applicationRepository.findByJobId(jobId);
    }

    private void createNotification(User user, String title, String message) {
        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .type(NotificationType.IN_APP)
                .status(NotificationStatus.PENDING)
                .build();
        notificationRepository.save(notification);
        log.info("Notification created for user: {} - Title: {}", user.getEmail(), title);
    }
}
