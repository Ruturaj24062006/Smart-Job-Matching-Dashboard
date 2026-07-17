package com.careermatch.backend.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResumeUploadedEvent implements Serializable {
    private UUID resumeId;
    private UUID studentId;
}
