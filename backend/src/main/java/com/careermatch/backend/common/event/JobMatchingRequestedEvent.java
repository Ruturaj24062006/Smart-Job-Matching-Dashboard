package com.careermatch.backend.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * Event published after a resume is fully processed (embedding + Groq extraction done).
 * Consumed by JobMatchingTaskListener to trigger the async hybrid RAG matching pipeline.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JobMatchingRequestedEvent implements Serializable {
    private UUID studentId;
    private UUID resumeId;
}
