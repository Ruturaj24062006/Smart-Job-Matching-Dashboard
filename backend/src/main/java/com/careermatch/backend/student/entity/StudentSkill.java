package com.careermatch.backend.student.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "student_skills")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentSkill {
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

    @Column(name = "proficiency_level")
    private String proficiencyLevel; // BEGINNER, INTERMEDIATE, ADVANCED
}
