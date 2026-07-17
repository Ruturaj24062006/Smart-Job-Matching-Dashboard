package com.careermatch.backend.application.dto;

import com.careermatch.backend.application.entity.ApplicationStatus;
import lombok.Data;

@Data
public class UpdateStatusRequest {
    private ApplicationStatus status;
    private String feedback;
}
