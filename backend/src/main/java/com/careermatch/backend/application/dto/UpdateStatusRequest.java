package com.careermatch.backend.application.dto;

import com.careermatch.backend.application.entity.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStatusRequest {
    private ApplicationStatus status;
    private String feedback;
}

