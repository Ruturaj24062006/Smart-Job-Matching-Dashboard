package com.careermatch.backend.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroqService {

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String modelName;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public String extractResumeProfile(String resumeText) {
        if ("mock-groq-key".equals(apiKey) || apiKey.isBlank()) {
            log.warn("Using mock resume profile extraction because Groq API Key is not set");
            return getMockProfileJson();
        }

        String systemPrompt = """
                You are an expert resume parser. Extract structured details from the following resume text.
                Return ONLY a JSON object that strictly adheres to this schema:
                {
                  "firstName": "First name",
                  "lastName": "Last name",
                  "skills": [
                    {"name": "Skill name", "proficiencyLevel": "BEGINNER/INTERMEDIATE/ADVANCED"}
                  ],
                  "education": [
                    {"institution": "University name", "degree": "B.S./M.S.", "fieldOfStudy": "Computer Science", "gpa": 3.8, "startDate": "YYYY-MM-DD", "endDate": "YYYY-MM-DD"}
                  ],
                  "experience": [
                    {"companyName": "Company name", "jobTitle": "Role title", "description": "Responsibilities", "startDate": "YYYY-MM-DD", "endDate": "YYYY-MM-DD"}
                  ],
                  "projects": [
                    {"name": "Project name", "description": "Project details", "repoUrl": "URL", "technologies": "Java, Spring Boot"}
                  ],
                  "certifications": [
                    {"name": "Cert name", "issuingOrganization": "Org name", "issueDate": "YYYY-MM-DD", "expirationDate": "YYYY-MM-DD"}
                  ],
                  "languages": "English, Spanish",
                  "githubUrl": "https://github.com/username",
                  "linkedinUrl": "https://linkedin.com/in/username",
                  "portfolioUrl": "https://portfolio.com",
                  "careerPreferences": "Software Engineer, Remote"
                }
                Make sure any missing date fields are omitted or set to null.
                """;

        return callGroq(systemPrompt, resumeText, true);
    }

    public String explainMatch(String resumeText, String jobDescription) {
        if ("mock-groq-key".equals(apiKey) || apiKey.isBlank()) {
            log.warn("Using mock explanation because Groq API Key is not set");
            return "This candidate shows strong technical skills matching the job requirements, but detailed AI match verification is currently in demo mode.";
        }

        String systemPrompt = """
                You are a technical recruiter. Explain why this candidate's resume matches the job description.
                Identify critical strengths, core matches, and summarize the overall suitability.
                Keep it concise, professional, and directly actionable.
                """;

        String userPrompt = String.format("RESUME:\n%s\n\nJOB DESCRIPTION:\n%s", resumeText, jobDescription);
        return callGroq(systemPrompt, userPrompt, false);
    }

    public String explainSkillGap(String resumeText, String jobDescription) {
        if ("mock-groq-key".equals(apiKey) || apiKey.isBlank()) {
            log.warn("Using mock skill gap because Groq API Key is not set");
            return "{\"missingSkills\": [\"AWS\", \"Kubernetes\"], \"roadmap\": \"Learn Docker container basics, then deploy services using AWS ECS or EKS.\"}";
        }

        String systemPrompt = """
                Compare the candidate's resume and the job description.
                Return a JSON object detailing missing skills and a brief roadmap:
                {
                  "missingSkills": ["skill1", "skill2"],
                  "roadmap": "Brief step-by-step recommendation to bridge the gap."
                }
                """;

        String userPrompt = String.format("RESUME:\n%s\n\nJOB DESCRIPTION:\n%s", resumeText, jobDescription);
        return callGroq(systemPrompt, userPrompt, true);
    }

    private String callGroq(String systemPrompt, String userPrompt, boolean requireJson) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));

            if (requireJson) {
                requestBody.put("response_format", Map.of("type", "json_object"));
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map> choices = (List<Map>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map message = (Map) choices.get(0).get("message");
                    if (message != null) {
                        return (String) message.get("content");
                    }
                }
            }
            throw new RuntimeException("Empty response from Groq API");
        } catch (Exception e) {
            log.error("Failed to fetch response from Groq: {}", e.getMessage());
            return requireJson ? "{}" : "Failed to obtain AI explanation.";
        }
    }

    private String getMockProfileJson() {
        return """
                {
                  "firstName": "John",
                  "lastName": "Doe",
                  "skills": [
                    {"name": "Java", "proficiencyLevel": "ADVANCED"},
                    {"name": "Spring Boot", "proficiencyLevel": "ADVANCED"},
                    {"name": "SQL", "proficiencyLevel": "INTERMEDIATE"},
                    {"name": "Angular", "proficiencyLevel": "INTERMEDIATE"}
                  ],
                  "education": [
                    {"institution": "State University", "degree": "Bachelor of Science", "fieldOfStudy": "Computer Science", "gpa": 3.85, "startDate": "2021-09-01", "endDate": "2025-05-15"}
                  ],
                  "experience": [
                    {"companyName": "TechCorp", "jobTitle": "Software Intern", "description": "Developed REST APIs using Java and Spring Boot, optimized query executions, and contributed to frontend dashboards in Angular.", "startDate": "2024-06-01", "endDate": "2024-08-31"}
                  ],
                  "projects": [
                    {"name": "E-Commerce System", "description": "Designed microservices-based shop backend with PostgreSQL and Redis caching.", "repoUrl": "https://github.com/johndoe/shop-api", "technologies": "Java, Spring Boot, PostgreSQL, Redis"}
                  ],
                  "certifications": [
                    {"name": "AWS Certified Developer", "issuingOrganization": "Amazon Web Services", "issueDate": "2025-01-10", "expirationDate": "2028-01-10"}
                  ],
                  "languages": "English, German",
                  "githubUrl": "https://github.com/johndoe",
                  "linkedinUrl": "https://linkedin.com/in/johndoe",
                  "portfolioUrl": "https://johndoe.dev",
                  "careerPreferences": "Software Engineer, Remote"
                }
                """;
    }
}
