package com.careermatch.backend.matching.dto;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class MatchResponse {
    private UUID id;
    private UUID jobId;
    private String jobTitle;
    private String companyName;
    private String location;
    private Double compositeScore;
    private boolean eligibilityStatus;
    private String salaryRange;
    private String jobType;
    private String workMode;
    private String experienceLevel;
    private String requiredSkills;
    private String preferredSkills;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime deadline;
}
