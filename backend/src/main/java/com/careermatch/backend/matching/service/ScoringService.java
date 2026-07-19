package com.careermatch.backend.matching.service;

import com.careermatch.backend.job.entity.Job;
import com.careermatch.backend.student.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic Scoring Engine implementing the exact 100 Marks Recommended Score Formula:
 *
 *  Factor                                  Weight
 *  ────────────────────────────────────────────────
 *  Skills Match                            35%
 *  Semantic Similarity (Embeddings)        25%
 *  Experience Match                        15%
 *  Education Match                         10%
 *  Projects Match                           5%
 *  Certifications                           5%
 *  Preferred Location / Work Mode           3%
 *  Job Preferences (Role, Salary, etc.)     2%
 *  ────────────────────────────────────────────────
 *  Total                                  100%
 */
@Service
@Slf4j
public class ScoringService {

    public double calculateCompositeScore(Student student, Job job) {
        return calculateCompositeScore(student, job, null);
    }

    public double calculateCompositeScore(Student student, Job job, float[] studentEmbedding) {
        double skillsScore      = calculateSkillsMatch(student, job);            // 35%
        double semanticScore    = calculateSemanticSimilarity(studentEmbedding, job.getEmbedding()); // 25%
        double experienceScore  = calculateExperienceMatch(student, job);         // 15%
        double educationScore   = calculateEducationMatch(student, job);          // 10%
        double projectsScore    = calculateProjectsMatch(student, job);           // 5%
        double certsScore       = calculateCertificationsScore(student, job);     // 5%
        double locationModeScore= calculateLocationWorkModeMatch(student, job);   // 3%
        double preferencesScore = calculatePreferencesMatch(student, job);        // 2%

        double composite = skillsScore + semanticScore + experienceScore + educationScore 
                         + projectsScore + certsScore + locationModeScore + preferencesScore;

        // Domain & Skill Mismatch Guard:
        // If student has explicit skills listed, but zero skill overlap AND low semantic match,
        // cap composite score to 0.0 to eliminate irrelevant matches (e.g. Marketing student getting Backend Dev job)
        boolean hasSkills = student.getSkills() != null && !student.getSkills().isEmpty();
        if (hasSkills && skillsScore < 1.0 && semanticScore < 5.0) {
            log.info("Domain mismatch detected between student {} skills and job {}: capping composite score to 0.", student.getId(), job.getId());
            composite = 0.0;
        }

        log.info("Scoring student {} for Job {}: Skills={}, Semantic={}, Exp={}, Edu={}, Proj={}, Certs={}, LocMode={}, Prefs={}, Total={}",
                student.getId(), job.getId(), skillsScore, semanticScore, experienceScore, educationScore, 
                projectsScore, certsScore, locationModeScore, preferencesScore, composite);

        return Math.round(composite * 10.0) / 10.0; // round to 1 decimal place
    }

    // 1. Skills Match (35%)
    public double calculateSkillsMatch(Student student, Job job) {
        if (student.getSkills() == null || student.getSkills().isEmpty()) {
            return 0.0;
        }

        String reqSkills  = job.getRequiredSkills() != null ? job.getRequiredSkills().toLowerCase() : "";
        String prefSkills = job.getPreferredSkills() != null ? job.getPreferredSkills().toLowerCase() : "";
        String reqs       = job.getRequirements() != null ? job.getRequirements().toLowerCase() : "";
        String desc       = job.getDescription() != null ? job.getDescription().toLowerCase() : "";
        String title      = job.getTitle() != null ? job.getTitle().toLowerCase() : "";
        String fullJobText = title + " " + reqSkills + " " + prefSkills + " " + reqs + " " + desc;

        Set<String> studentSkills = student.getSkills().stream()
                .map(s -> s.getName() != null ? s.getName().toLowerCase().trim() : "")
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        if (studentSkills.isEmpty()) return 0.0;

        long matchedCount = studentSkills.stream()
                .filter(fullJobText::contains)
                .count();

        double ratio = (double) matchedCount / Math.max(3.0, studentSkills.size());
        return Math.min(35.0, ratio * 35.0);
    }

    // 2. Semantic Similarity (Embeddings) (25%)
    public double calculateSemanticSimilarity(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length == 0 || v2.length == 0 || v1.length != v2.length) {
            return 12.5; // Default 50% semantic baseline when vectors unavailable
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }

        if (normA == 0 || normB == 0) return 0.0;

        double similarity = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
        similarity = Math.max(0.0, Math.min(1.0, similarity)); // Clamp to [0, 1]

        return Math.round(similarity * 25.0 * 10.0) / 10.0;
    }

    // 3. Experience Match (15%)
    public double calculateExperienceMatch(Student student, Job job) {
        double years = calculateYearsOfExperience(student);
        String expLevel = job.getExperienceLevel() != null ? job.getExperienceLevel().toUpperCase() : "ENTRY_LEVEL";

        if (expLevel.contains("SENIOR") || expLevel.contains("EXECUTIVE")) {
            if (years >= 5.0) return 15.0;
            return (years / 5.0) * 15.0;
        } else if (expLevel.contains("MID") || expLevel.contains("INTERMEDIATE")) {
            if (years >= 2.0) return 15.0;
            return (years / 2.0) * 15.0;
        } else {
            // Entry level / Internship
            return 15.0;
        }
    }

    // 4. Education Match (10%)
    public double calculateEducationMatch(Student student, Job job) {
        if (student.getEducation() == null || student.getEducation().isEmpty()) {
            return 0.0;
        }

        double score = 5.0; // Base score for having an education record

        String reqEdu = job.getEducationLevel() != null ? job.getEducationLevel().toLowerCase() : "";
        String jobText = (job.getTitle() + " " + job.getRequirements() + " " + job.getDescription()).toLowerCase();

        for (StudentEducation edu : student.getEducation()) {
            String degree = edu.getDegree() != null ? edu.getDegree().toLowerCase() : "";
            String field = edu.getFieldOfStudy() != null ? edu.getFieldOfStudy().toLowerCase() : "";

            if (!degree.isBlank() && (jobText.contains(degree) || reqEdu.contains(degree))) {
                score += 2.5;
            }
            if (!field.isBlank() && jobText.contains(field)) {
                score += 2.5;
            }

            if (job.getGpaCutoff() != null && edu.getGpa() != null) {
                if (edu.getGpa() >= job.getGpaCutoff()) {
                    score += 1.0;
                }
            }
        }

        return Math.min(10.0, score);
    }

    // 5. Projects Match (5%)
    public double calculateProjectsMatch(Student student, Job job) {
        if (student.getProjects() == null || student.getProjects().isEmpty()) {
            return 0.0;
        }

        String jobText = (job.getTitle() + " " + job.getDescription() + " " + job.getRequirements()).toLowerCase();
        long matches = 0;

        for (StudentProject project : student.getProjects()) {
            String tech = project.getTechnologies() != null ? project.getTechnologies().toLowerCase() : "";
            String desc = project.getDescription() != null ? project.getDescription().toLowerCase() : "";
            String name = project.getName() != null ? project.getName().toLowerCase() : "";

            if (jobText.contains(name) || (!tech.isBlank() && jobText.contains(tech)) || (!desc.isBlank() && jobText.contains(desc))) {
                matches++;
            }
        }

        double ratio = (double) matches / student.getProjects().size();
        return Math.min(5.0, ratio * 5.0);
    }

    // 6. Certifications (5%)
    public double calculateCertificationsScore(Student student, Job job) {
        if (student.getCertifications() == null || student.getCertifications().isEmpty()) {
            return 0.0;
        }

        String jobText = (job.getTitle() + " " + job.getRequirements() + " " + job.getDescription()).toLowerCase();
        long matches = 0;

        for (StudentCertification cert : student.getCertifications()) {
            String cName = cert.getName() != null ? cert.getName().toLowerCase() : "";
            if (!cName.isBlank() && (jobText.contains(cName) || cName.contains("certified") || cName.contains("aws") || cName.contains("google") || cName.contains("microsoft"))) {
                matches++;
            }
        }

        if (matches > 0) return 5.0;
        return 2.5; // Baseline credit for having completed professional certifications
    }

    // 7. Preferred Location / Work Mode (3%)
    public double calculateLocationWorkModeMatch(Student student, Job job) {
        double score = 0.0;

        String studentLoc = ((student.getCareerPreferences() != null ? student.getCareerPreferences() : "") + " " +
                (student.getBio() != null ? student.getBio() : "")).toLowerCase();
        String jobLoc = job.getLocation() != null ? job.getLocation().toLowerCase() : "";
        String workMode = job.getWorkMode() != null ? job.getWorkMode().toUpperCase() : "HYBRID";

        if ("REMOTE".equalsIgnoreCase(workMode)) {
            score += 3.0; // Remote fits all locations
        } else if (!jobLoc.isBlank() && !studentLoc.isBlank() && (studentLoc.contains(jobLoc) || jobLoc.contains(studentLoc))) {
            score += 3.0;
        } else {
            score += 1.5; // Partial location fit baseline
        }

        return Math.min(3.0, score);
    }

    // 8. Job Preferences (Role, Salary, etc.) (2%)
    public double calculatePreferencesMatch(Student student, Job job) {
        if (student.getCareerPreferences() == null || student.getCareerPreferences().isBlank()) {
            return 1.0; // Baseline neutral preference score
        }

        String prefs = student.getCareerPreferences().toLowerCase();
        String jobTitle = job.getTitle() != null ? job.getTitle().toLowerCase() : "";
        String salaryRange = job.getSalaryRange() != null ? job.getSalaryRange().toLowerCase() : "";

        double score = 0.0;
        if (prefs.contains(jobTitle) || jobTitle.contains(prefs)) {
            score += 1.5;
        }
        if (!salaryRange.isBlank() && prefs.contains("salary")) {
            score += 0.5;
        }

        return Math.min(2.0, Math.max(1.0, score));
    }

    // ── Backward-compatible sub-score delegates for API details responses ───
    public double calculateTechnicalFit(Student student, Job job)  { return calculateSkillsMatch(student, job); }
    public double calculateProjectFit(Student student, Job job)    { return calculateProjectsMatch(student, job); }
    public double calculateExperienceFit(Student student, Job job) { return calculateExperienceMatch(student, job); }
    public double calculateDomainFit(Student student, Job job)     { return calculateSkillsMatch(student, job) * 0.3; }
    public double calculateBehavioralFit(Student student, Job job) { return calculateProjectsMatch(student, job); }
    public double calculateEduCertFit(Student student)             { return calculateCertificationsScore(student, null); }

    private double calculateYearsOfExperience(Student student) {
        if (student.getExperience() == null || student.getExperience().isEmpty()) {
            return 0.0;
        }

        long totalDays = 0;
        for (StudentExperience exp : student.getExperience()) {
            LocalDate start = exp.getStartDate();
            LocalDate end = exp.getEndDate() != null ? exp.getEndDate() : LocalDate.now();
            if (start != null) {
                totalDays += ChronoUnit.DAYS.between(start, end);
            }
        }
        return (double) totalDays / 365.25;
    }
}
