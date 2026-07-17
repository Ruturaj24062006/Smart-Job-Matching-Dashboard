package com.careermatch.backend.application.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ApplicationResponse {
    private UUID id;
    private UUID jobId;
    private String jobTitle;
    private String companyName;
    private String studentName;
    private String resumeUrl;
    private String status;
    private String coverLetter;
    private String feedback;
    private LocalDateTime createdAt;
}
