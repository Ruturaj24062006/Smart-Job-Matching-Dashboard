package com.careermatch.backend.resume.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "resume_chunks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    @ToString.Exclude
    private Resume resume;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    // Vector embedding for dense search (384 dimensions)
    @org.hibernate.annotations.Type(com.careermatch.backend.common.converter.PgVectorUserType.class)
    @Column(name = "embedding", columnDefinition = "vector(384)")
    private float[] embedding;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
