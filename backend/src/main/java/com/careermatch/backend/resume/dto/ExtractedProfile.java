package com.careermatch.backend.resume.dto;

import lombok.Data;
import java.util.List;

@Data
public class ExtractedProfile {
    private String firstName;
    private String lastName;
    private List<SkillDto> skills;
    private List<EducationDto> education;
    private List<ExperienceDto> experience;
    private List<ProjectDto> projects;
    private List<CertificationDto> certifications;
    private String languages;
    private String githubUrl;
    private String linkedinUrl;
    private String portfolioUrl;
    private String careerPreferences;

    @Data
    public static class SkillDto {
        private String name;
        private String proficiencyLevel;
    }

    @Data
    public static class EducationDto {
        private String institution;
        private String degree;
        private String fieldOfStudy;
        private Double gpa;
        private String startDate;
        private String endDate;
    }

    @Data
    public static class ExperienceDto {
        private String companyName;
        private String jobTitle;
        private String description;
        private String startDate;
        private String endDate;
    }

    @Data
    public static class ProjectDto {
        private String name;
        private String description;
        private String repoUrl;
        private String technologies;
    }

    @Data
    public static class CertificationDto {
        private String name;
        private String issuingOrganization;
        private String issueDate;
        private String expirationDate;
    }
}
