package com.careermatch.backend.recruiter.entity;

import com.careermatch.backend.auth.entity.User;
import com.careermatch.backend.company.entity.Company;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "recruiters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recruiter implements Persistable<UUID> {

    @Id
    private UUID id; // Primary key matches User's ID

    @Transient
    @Builder.Default
    private boolean isNewEntity = true;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    @ToString.Exclude
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "job_title")
    private String jobTitle;

    @Builder.Default
    @Column(name = "is_verified", nullable = false)
    private boolean isVerified = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Override
    public boolean isNew() {
        return isNewEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNewEntity = false;
    }
}
