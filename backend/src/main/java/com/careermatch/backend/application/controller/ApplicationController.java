package com.careermatch.backend.application.controller;

import com.careermatch.backend.application.dto.ApplicationResponse;
import com.careermatch.backend.application.dto.ApplyRequest;
import com.careermatch.backend.application.dto.UpdateStatusRequest;
import com.careermatch.backend.application.entity.Application;
import com.careermatch.backend.application.service.ApplicationService;
import com.careermatch.backend.common.ApiResponse;
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
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@Tag(name = "Application Workflow", description = "Endpoints for applying to jobs, tracking application statuses, and recruiter reviews")
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping("/apply/{jobId}")
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    @Operation(summary = "Apply to a job with the current active resume")
    public ResponseEntity<ApiResponse<ApplicationResponse>> applyToJob(
            @PathVariable("jobId") UUID jobId,
            @Valid @RequestBody ApplyRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Application app = applicationService.applyToJob(jobId, request, email);
        return ResponseEntity.ok(ApiResponse.success("Applied to job successfully", mapToResponse(app)));
    }

    @PutMapping("/{applicationId}/status")
    @PreAuthorize("hasAuthority('ROLE_RECRUITER')")
    @Operation(summary = "Update applicant status (e.g. Shortlist, Interview, Reject) with feedback")
    public ResponseEntity<ApiResponse<ApplicationResponse>> updateStatus(
            @PathVariable("applicationId") UUID applicationId,
            @Valid @RequestBody UpdateStatusRequest request) {
        Application app = applicationService.updateApplicationStatus(applicationId, request);
        return ResponseEntity.ok(ApiResponse.success("Application status updated", mapToResponse(app)));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    @Operation(summary = "Get list of applications submitted by the logged-in student")
    public ResponseEntity<ApiResponse<List<ApplicationResponse>>> getMyApplications() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        List<Application> apps = applicationService.getApplicationsForStudent(email);
        List<ApplicationResponse> response = apps.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/job/{jobId}")
    @PreAuthorize("hasAuthority('ROLE_RECRUITER')")
    @Operation(summary = "Get all applications submitted for a job post")
    public ResponseEntity<ApiResponse<List<ApplicationResponse>>> getJobApplications(@PathVariable("jobId") UUID jobId) {
        List<Application> apps = applicationService.getApplicationsForJob(jobId);
        List<ApplicationResponse> response = apps.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private ApplicationResponse mapToResponse(Application app) {
        String studentName = (app.getStudent().getFirstName() != null ? app.getStudent().getFirstName() : "") + " " +
                (app.getStudent().getLastName() != null ? app.getStudent().getLastName() : "");
        return ApplicationResponse.builder()
                .id(app.getId())
                .jobId(app.getJob().getId())
                .jobTitle(app.getJob().getTitle())
                .companyName(app.getJob().getCompany().getName())
                .studentName(studentName.trim())
                .resumeUrl(app.getResume().getFileUrl())
                .status(app.getStatus().name())
                .coverLetter(app.getCoverLetter())
                .feedback(app.getFeedback())
                .createdAt(app.getCreatedAt())
                .build();
    }
}
