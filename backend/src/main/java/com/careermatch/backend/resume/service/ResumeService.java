package com.careermatch.backend.resume.service;

import com.careermatch.backend.ai.service.EmbeddingService;
import com.careermatch.backend.ai.service.GroqService;
import com.careermatch.backend.common.event.JobMatchingRequestedEvent;
import com.careermatch.backend.config.QueueConfig;
import com.careermatch.backend.exception.BadRequestException;
import com.careermatch.backend.exception.ResourceNotFoundException;
import com.careermatch.backend.resume.dto.ExtractedProfile;
import com.careermatch.backend.resume.entity.Resume;
import com.careermatch.backend.resume.repository.ResumeRepository;
import com.careermatch.backend.student.entity.*;
import com.careermatch.backend.student.repository.StudentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careermatch.backend.resume.entity.ResumeChunk;
import com.careermatch.backend.resume.repository.ResumeChunkRepository;
import java.util.List;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final ResumeChunkRepository resumeChunkRepository;
    private final StudentRepository studentRepository;
    private final EmbeddingService embeddingService;
    private final GroqService groqService;
    private final RabbitTemplate rabbitTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    @Qualifier("resumeProcessingExecutor")
    private final Executor resumeExecutor;

    /**
     * Background processing pipeline for an uploaded resume.
     * <p>
     * Complete RAG Pipeline:
     * 1. Load resume (parsedText present)
     * 2. Chunk text into 500-char segments with 100-char overlap
     * 3. Generate 384-dim embeddings for each chunk via all-MiniLM-L6-v2
     * 4. Persist chunks & vectors in pgvector (resume_chunks table)
     * 5. Extract structured JSON profile via Groq LLM (llama-3.3-70b-versatile)
     * 6. Persist structured profile in PostgreSQL student entities
     * 7. Generate main resume embedding vector
     * 8. Mark status = SUCCESS & activate
     * 9. Fire JobMatchingRequestedEvent → triggers hybrid RAG matching & 100-mark scoring engine
     */
    @Transactional
    public void processResume(UUID resumeId) {
        long startProcess = System.currentTimeMillis();
        log.info("[RAG_PIPELINE][STAGE 1] Starting processing for resume ID: {}", resumeId);

        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found: " + resumeId));

        final String parsedText = resume.getParsedText();
        if (parsedText == null || parsedText.isBlank()) {
            log.error("[RAG_PIPELINE][STAGE 1-2] Resume {} has no parsedText — text extraction failed. Marking FAILED.", resumeId);
            resume.setProcessingStatus("FAILED");
            resumeRepository.save(resume);
            fireJobMatchingEvent(resume.getStudent().getId(), resumeId);
            return;
        }
        log.info("[RAG_PIPELINE][STAGE 2] Raw text extracted successfully. Length: {} chars", parsedText.length());

        try {
            // ── STAGE 3: Chunk Text & Generate Embeddings for Chunks (pgvector) ──────
            long tChunk = System.currentTimeMillis();
            List<String> textChunks = createTextChunks(parsedText, 500, 100);
            log.info("[RAG_PIPELINE][STAGE 3] Split parsed text into {} chunks.", textChunks.size());

            try {
                resumeChunkRepository.deleteByResumeId(resumeId);
                for (int i = 0; i < textChunks.size(); i++) {
                    String chunkContent = textChunks.get(i);
                    float[] chunkVector = embeddingService.generateEmbedding(chunkContent);
                    ResumeChunk chunkEntity = ResumeChunk.builder()
                            .resume(resume)
                            .chunkIndex(i)
                            .content(chunkContent)
                            .embedding(chunkVector)
                            .build();
                    resumeChunkRepository.save(chunkEntity);
                }
                log.info("[RAG_PIPELINE][STAGE 3-4] Saved {} resume chunks with embeddings to pgvector in {} ms.",
                        textChunks.size(), System.currentTimeMillis() - tChunk);
            } catch (Exception e) {
                log.warn("[RAG_PIPELINE][STAGE 3-4] Chunk vector storage failed: {}. Continuing main profile extraction.", e.getMessage());
            }

            // ── STAGE 5: Extract structured JSON profile via Groq ───────────────────
            long tGroq = System.currentTimeMillis();
            log.info("[RAG_PIPELINE][STAGE 5] Invoking Groq LLM for structured JSON extraction...");
            String jsonProfile = groqService.extractResumeProfile(parsedText);
            log.info("[RAG_PIPELINE][STAGE 5] Groq extraction completed in {} ms. JSON length: {} chars",
                    System.currentTimeMillis() - tGroq, jsonProfile != null ? jsonProfile.length() : 0);

            if (jsonProfile == null || jsonProfile.isBlank() || "{}".equals(jsonProfile.trim())) {
                log.error("[RAG_PIPELINE][STAGE 5] Groq returned empty JSON for resume {}. Marking FAILED.", resumeId);
                resume.setProcessingStatus("FAILED");
                resumeRepository.save(resume);
                fireJobMatchingEvent(resume.getStudent().getId(), resumeId);
                return;
            }

            // ── STAGE 6: Parse JSON DTO & populate PostgreSQL student relations ─────
            long tDb = System.currentTimeMillis();
            log.info("[RAG_PIPELINE][STAGE 6] Deserializing structured JSON and updating PostgreSQL student profile...");
            ExtractedProfile profile = objectMapper.readValue(jsonProfile, ExtractedProfile.class);
            Student student = studentRepository.findById(resume.getStudent().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + resume.getStudent().getId()));
            updateStudentProfile(student, profile);

            log.info("[RAG_PIPELINE][STAGE 6] PostgreSQL profile updated in {} ms. Skills: {}, Experience: {}, Projects: {}",
                    System.currentTimeMillis() - tDb,
                    student.getSkills() != null ? student.getSkills().size() : 0,
                    student.getExperience() != null ? student.getExperience().size() : 0,
                    student.getProjects() != null ? student.getProjects().size() : 0);

            // ── STAGE 7: Generate overall vector embedding ───────────────────────────
            long tEmbed = System.currentTimeMillis();
            String structuredText = buildStructuredTextFromProfile(profile, student);
            log.info("[RAG_PIPELINE][STAGE 7] Generating 384-dim vector embedding from structured profile text (length: {} chars)...", structuredText.length());

            float[] vector;
            try {
                vector = embeddingService.generateEmbedding(structuredText);
                log.info("[RAG_PIPELINE][STAGE 7] Main embedding generated successfully in {} ms (dims: {})",
                        System.currentTimeMillis() - tEmbed, vector.length);
            } catch (Exception e) {
                log.warn("[RAG_PIPELINE][STAGE 7] Embedding generation failed ({} ms): {}. Using fallback zero-vector.",
                        System.currentTimeMillis() - tEmbed, e.getMessage());
                vector = new float[384];
                for (int i = 0; i < 384; i++) vector[i] = 0.01f;
            }

            // ── STAGE 8: Persist resume entity & activate ───────────────────────────
            resume.setEmbedding(vector);
            resume.setExtractedJson(jsonProfile);
            resume.setProcessingStatus("SUCCESS");
            saveResumeAndDeactivateOthers(resume);

            log.info("[RAG_PIPELINE][SUMMARY] Resume {} processed successfully in {} ms. Triggering RAG matching engine...",
                    resumeId, System.currentTimeMillis() - startProcess);

            // ── STAGE 9: Fire async job matching pipeline ──────────────────────────
            fireJobMatchingEvent(student.getId(), resumeId);

        } catch (Exception e) {
            log.error("[RAG_PIPELINE][FAILURE] Critical failure processing resume {} after {} ms: {}",
                    resumeId, System.currentTimeMillis() - startProcess, e.getMessage(), e);
            try {
                resume.setProcessingStatus("FAILED");
                resumeRepository.save(resume);
                fireJobMatchingEvent(resume.getStudent().getId(), resumeId);
            } catch (Exception ex) {
                log.error("[RAG_PIPELINE][FAILURE] Failed to persist FAILED status for resume {}: {}", resumeId, ex.getMessage());
            }
        }
    }

    private List<String> createTextChunks(String text, int chunkSize, int overlap) {
        List<String> chunks = new java.util.ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        int length = text.length();
        int start = 0;
        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end == length) break;
            start += (chunkSize - overlap);
        }
        return chunks;
    }

    /**
     * Constructs clean, normalized text representation of structured profile for embedding generation.
     */
    private String buildStructuredTextFromProfile(ExtractedProfile profile, Student student) {
        StringBuilder sb = new StringBuilder();
        if (profile.getSkills() != null && !profile.getSkills().isEmpty()) {
            sb.append("Skills: ");
            for (var s : profile.getSkills()) {
                if (s.getName() != null) sb.append(s.getName()).append(", ");
            }
            sb.append(". ");
        }
        if (profile.getExperience() != null && !profile.getExperience().isEmpty()) {
            sb.append("Experience: ");
            for (var exp : profile.getExperience()) {
                if (exp.getJobTitle() != null) sb.append(exp.getJobTitle()).append(" ");
                if (exp.getCompanyName() != null) sb.append("at ").append(exp.getCompanyName()).append(". ");
                if (exp.getDescription() != null) sb.append(exp.getDescription()).append(" ");
            }
            sb.append(". ");
        }
        if (profile.getProjects() != null && !profile.getProjects().isEmpty()) {
            sb.append("Projects: ");
            for (var proj : profile.getProjects()) {
                if (proj.getName() != null) sb.append(proj.getName()).append(" ");
                if (proj.getTechnologies() != null) sb.append("(").append(proj.getTechnologies()).append(") ");
                if (proj.getDescription() != null) sb.append(proj.getDescription()).append(". ");
            }
            sb.append(". ");
        }
        if (profile.getEducation() != null && !profile.getEducation().isEmpty()) {
            sb.append("Education: ");
            for (var edu : profile.getEducation()) {
                if (edu.getDegree() != null) sb.append(edu.getDegree()).append(" ");
                if (edu.getFieldOfStudy() != null) sb.append("in ").append(edu.getFieldOfStudy()).append(" ");
                if (edu.getInstitution() != null) sb.append("from ").append(edu.getInstitution()).append(". ");
            }
            sb.append(". ");
        }
        if (profile.getCareerPreferences() != null && !profile.getCareerPreferences().isBlank()) {
            sb.append("Preferences: ").append(profile.getCareerPreferences()).append(". ");
        }
        if (student.getBio() != null && !student.getBio().isBlank()) {
            sb.append("Bio: ").append(student.getBio());
        }
        return sb.toString().trim();
    }



    /**
     * Publishes a JobMatchingRequestedEvent to RabbitMQ so the matching
     * pipeline runs asynchronously on its own dedicated queue.
     * Falls back to a local Spring ApplicationEvent if RabbitMQ is unavailable.
     */
    private void fireJobMatchingEvent(UUID studentId, UUID resumeId) {
        JobMatchingRequestedEvent event = new JobMatchingRequestedEvent(studentId, resumeId);
        try {
            rabbitTemplate.convertAndSend(
                    QueueConfig.EXCHANGE,
                    QueueConfig.JOB_MATCHING_ROUTING_KEY,
                    event);
            log.info("JobMatchingRequestedEvent dispatched via RabbitMQ for student {}.", studentId);
        } catch (Exception e) {
            log.warn("RabbitMQ unavailable — firing local JobMatchingRequestedEvent for student {}: {}",
                    studentId, e.getMessage());
            eventPublisher.publishEvent(event);
        }
    }

    @Transactional
    public void saveResumeAndDeactivateOthers(Resume resume) {
        Student student = resume.getStudent();
        resumeRepository.deactivateOtherResumes(student.getId(), resume.getId());
        resume.setCurrent(true);
        resumeRepository.save(resume);
    }

    @Transactional
    public void confirmResumeExtractedProfile(UUID resumeId) {
        log.info("Confirming resume extraction for resume ID: {}", resumeId);
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found: " + resumeId));

        if (resume.getExtractedJson() == null || resume.getExtractedJson().isBlank()) {
            throw new BadRequestException("Resume parsing has not completed or has no extracted data.");
        }

        try {
            Student student = resume.getStudent();
            ExtractedProfile profile = objectMapper.readValue(resume.getExtractedJson(), ExtractedProfile.class);
            updateStudentProfile(student, profile);
            log.info("Student profile updated from resume extraction for student ID: {}", student.getId());
        } catch (Exception e) {
            log.error("Failed to update student profile from resume ID: {}", resumeId, e);
            throw new RuntimeException("Failed to parse extracted JSON and update profile: " + e.getMessage(), e);
        }
    }

    private void updateStudentProfile(Student student, ExtractedProfile profile) {
        if ((student.getFirstName() == null || student.getFirstName().isBlank()) && profile.getFirstName() != null && !profile.getFirstName().isBlank()) {
            student.setFirstName(profile.getFirstName());
        }
        if ((student.getLastName() == null || student.getLastName().isBlank()) && profile.getLastName() != null && !profile.getLastName().isBlank()) {
            student.setLastName(profile.getLastName());
        }
        if (profile.getLanguages() != null) student.setLanguages(profile.getLanguages());
        if (profile.getGithubUrl() != null) student.setGithubUrl(profile.getGithubUrl());
        if (profile.getLinkedinUrl() != null) student.setLinkedinUrl(profile.getLinkedinUrl());
        if (profile.getPortfolioUrl() != null) student.setPortfolioUrl(profile.getPortfolioUrl());
        if (profile.getCareerPreferences() != null) student.setCareerPreferences(profile.getCareerPreferences());

        // Clear existing profile relations before re-mapping from extracted profile
        student.getSkills().clear();
        student.getProjects().clear();
        student.getExperience().clear();
        student.getEducation().clear();
        student.getCertifications().clear();

        int scoreWeight = 20; // base profile points

        // Map skills (Technical Fit basis — 20 pts)
        if (profile.getSkills() != null && !profile.getSkills().isEmpty()) {
            scoreWeight += 20;
            profile.getSkills().forEach(s -> student.getSkills().add(
                    StudentSkill.builder()
                            .student(student)
                            .name(s.getName())
                            .proficiencyLevel(s.getProficiencyLevel() != null ? s.getProficiencyLevel() : "INTERMEDIATE")
                            .build()
            ));
        }

        // Map education (20 pts)
        if (profile.getEducation() != null && !profile.getEducation().isEmpty()) {
            scoreWeight += 20;
            profile.getEducation().forEach(e -> student.getEducation().add(
                    StudentEducation.builder()
                            .student(student)
                            .institution(e.getInstitution())
                            .degree(e.getDegree())
                            .fieldOfStudy(e.getFieldOfStudy())
                            .gpa(e.getGpa())
                            .startDate(parseDate(e.getStartDate()))
                            .endDate(parseDate(e.getEndDate()))
                            .build()
            ));
        }

        // Map experience (20 pts)
        if (profile.getExperience() != null && !profile.getExperience().isEmpty()) {
            scoreWeight += 20;
            profile.getExperience().forEach(ex -> student.getExperience().add(
                    StudentExperience.builder()
                            .student(student)
                            .companyName(ex.getCompanyName())
                            .jobTitle(ex.getJobTitle())
                            .description(ex.getDescription())
                            .startDate(parseDate(ex.getStartDate()))
                            .endDate(parseDate(ex.getEndDate()))
                            .build()
            ));
        }

        // Map projects (20 pts)
        if (profile.getProjects() != null && !profile.getProjects().isEmpty()) {
            scoreWeight += 20;
            profile.getProjects().forEach(p -> student.getProjects().add(
                    StudentProject.builder()
                            .student(student)
                            .name(p.getName())
                            .description(p.getDescription())
                            .repoUrl(p.getRepoUrl())
                            .technologies(p.getTechnologies())
                            .build()
            ));
        }

        // Map certifications (no points — bonus tier)
        if (profile.getCertifications() != null && !profile.getCertifications().isEmpty()) {
            profile.getCertifications().forEach(c -> student.getCertifications().add(
                    StudentCertification.builder()
                            .student(student)
                            .name(c.getName())
                            .issuingOrganization(c.getIssuingOrganization())
                            .issueDate(parseDate(c.getIssueDate()))
                            .expirationDate(parseDate(c.getExpirationDate()))
                            .build()
            ));
        }

        student.setProfileCompletedPct(scoreWeight);
        studentRepository.save(student);
        log.info("Updated Student Profile. Completion Score: {}%", scoreWeight);
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank() || "null".equalsIgnoreCase(dateStr)) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (Exception e) {
            log.debug("Failed parsing date string: {}, returning null", dateStr);
            return null;
        }
    }
}
