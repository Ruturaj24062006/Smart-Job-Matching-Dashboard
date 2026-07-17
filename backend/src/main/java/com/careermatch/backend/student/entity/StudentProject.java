package com.careermatch.backend.student.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "student_projects")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProject {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private Student student;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "repo_url")
    private String repoUrl;

    private String technologies; // comma-separated or json
}
