package com.careermatch.backend.job.controller;

import com.careermatch.backend.common.ApiResponse;
import com.careermatch.backend.job.dto.JobRequest;
import com.careermatch.backend.job.dto.JobResponse;
import com.careermatch.backend.job.entity.Job;
import com.careermatch.backend.job.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Tag(name = "Job Management", description = "Endpoints for posting, editing, listing, and closing job offers")
public class JobController {

    private final JobService jobService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_RECRUITER')")
    @Operation(summary = "Post a new job and trigger AI matching index update")
    public ResponseEntity<ApiResponse<JobResponse>> createJob(@Valid @RequestBody JobRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Job job = jobService.createJob(request, email);
        return ResponseEntity.ok(ApiResponse.success("Job created successfully", mapToResponse(job)));
    }

    @PutMapping("/{jobId}")
    @PreAuthorize("hasAuthority('ROLE_RECRUITER')")
    @Operation(summary = "Update an existing job detail and rebuild embeddings")
    public ResponseEntity<ApiResponse<JobResponse>> editJob(@PathVariable("jobId") UUID jobId, @Valid @RequestBody JobRequest request) {
        Job job = jobService.editJob(jobId, request);
        return ResponseEntity.ok(ApiResponse.success("Job updated successfully", mapToResponse(job)));
    }

    @DeleteMapping("/{jobId}")
    @PreAuthorize("hasAuthority('ROLE_RECRUITER')")
    @Operation(summary = "Remove a job posting from the system")
    public ResponseEntity<ApiResponse<String>> deleteJob(@PathVariable("jobId") UUID jobId) {
        jobService.deleteJob(jobId);
        return ResponseEntity.ok(ApiResponse.success("Job deleted successfully", "Deleted"));
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get detailed information of a job offer")
    public ResponseEntity<ApiResponse<JobResponse>> getJob(@PathVariable("jobId") UUID jobId) {
        Job job = jobService.getJobById(jobId);
        return ResponseEntity.ok(ApiResponse.success(mapToResponse(job)));
    }

    @GetMapping
    @Operation(summary = "List all published jobs")
    public ResponseEntity<ApiResponse<List<JobResponse>>> getPublishedJobs() {
        List<Job> jobs = jobService.getAllPublishedJobs();
        List<JobResponse> response = jobs.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private JobResponse mapToResponse(Job job) {
        return JobResponse.builder()
                .id(job.getId())
                .companyName(job.getCompany().getName())
                .title(job.getTitle())
                .description(job.getDescription())
                .requirements(job.getRequirements())
                .location(job.getLocation())
                .jobType(job.getJobType().name())
                .experienceLevel(job.getExperienceLevel())
                .salaryRange(job.getSalaryRange())
                .status(job.getStatus().name())
                .createdAt(job.getCreatedAt())
                .build();
    }
}
