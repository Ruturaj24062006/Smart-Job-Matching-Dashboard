package com.careermatch.backend.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JobPostedEvent implements Serializable {
    private UUID jobId;
    private UUID companyId;
}
