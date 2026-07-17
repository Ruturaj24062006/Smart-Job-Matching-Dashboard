package com.careermatch.backend.job.service;

import com.careermatch.backend.ai.service.EmbeddingService;
import com.careermatch.backend.auth.entity.User;
import com.careermatch.backend.auth.repository.UserRepository;
import com.careermatch.backend.common.event.JobPostedEvent;
import com.careermatch.backend.config.QueueConfig;
import com.careermatch.backend.exception.BadRequestException;
import com.careermatch.backend.exception.ResourceNotFoundException;
import com.careermatch.backend.job.dto.JobRequest;
import com.careermatch.backend.job.entity.Job;
import com.careermatch.backend.job.entity.JobStatus;
import com.careermatch.backend.job.repository.JobRepository;
import com.careermatch.backend.recruiter.entity.Recruiter;
import com.careermatch.backend.recruiter.repository.RecruiterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final JobRepository jobRepository;
    private final RecruiterRepository recruiterRepository;
    private final UserRepository userRepository;
    private final EmbeddingService embeddingService;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public Job createJob(JobRequest request, String recruiterEmail) {
        User user = userRepository.findByEmail(recruiterEmail)
                .orElseThrow(() -> new BadRequestException("Recruiter user not found"));

        Recruiter recruiter = recruiterRepository.findById(user.getId())
                .orElseThrow(() -> new BadRequestException("Recruiter profile not initialized"));

        if (recruiter.getCompany() == null) {
            throw new BadRequestException("Recruiter must be associated with a company to post jobs");
        }

        // Generate embedding for Job search context
        String context = request.getTitle() + " " + request.getDescription() + " " + request.getRequirements();
        float[] vector = embeddingService.generateEmbedding(context);

        Job job = Job.builder()
                .recruiter(recruiter)
                .company(recruiter.getCompany())
                .title(request.getTitle())
                .description(request.getDescription())
                .requirements(request.getRequirements())
                .location(request.getLocation())
                .jobType(request.getJobType())
                .experienceLevel(request.getExperienceLevel())
                .salaryRange(request.getSalaryRange())
                .status(JobStatus.PUBLISHED) // Auto-publish for simplicity
                .embedding(vector)
                .build();

        Job saved = jobRepository.save(job);
        log.info("Posted job ID: {} by Recruiter: {}", saved.getId(), recruiter.getId());

        // Dispatch JobPostedEvent to trigger match updates for all students
        JobPostedEvent event = new JobPostedEvent(saved.getId(), recruiter.getCompany().getId());
        rabbitTemplate.convertAndSend(QueueConfig.EXCHANGE, QueueConfig.JOB_POSTED_ROUTING_KEY, event);

        return saved;
    }

    @Transactional
    public Job editJob(UUID jobId, JobRequest request) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));

        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setRequirements(request.getRequirements());
        job.setLocation(request.getLocation());
        job.setJobType(request.getJobType());
        job.setExperienceLevel(request.getExperienceLevel());
        job.setSalaryRange(request.getSalaryRange());

        // Re-generate embedding
        String context = request.getTitle() + " " + request.getDescription() + " " + request.getRequirements();
        float[] vector = embeddingService.generateEmbedding(context);
        job.setEmbedding(vector);

        return jobRepository.save(job);
    }

    @Transactional
    public void deleteJob(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
        jobRepository.delete(job);
    }

    public Job getJobById(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
    }

    public List<Job> getJobsByCompany(UUID companyId) {
        return jobRepository.findByCompanyId(companyId);
    }

    public List<Job> getAllPublishedJobs() {
        return jobRepository.findByStatus(JobStatus.PUBLISHED);
    }
}
