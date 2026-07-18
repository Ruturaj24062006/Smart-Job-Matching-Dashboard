package com.careermatch.backend.matching.service;

import com.careermatch.backend.job.entity.Job;
import com.careermatch.backend.job.repository.JobRepository;
import com.careermatch.backend.resume.entity.Resume;
import com.careermatch.backend.resume.repository.ResumeRepository;
import com.careermatch.backend.student.entity.Student;
import com.careermatch.backend.student.entity.StudentSkill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;

    /**
     * Hybrid RAG search for top matching jobs for a given student.
     *
     * Algorithm:
     *   - Dense leg: pgvector cosine similarity on resume embedding (top-100)
     *   - Sparse leg: PostgreSQL tsvector BM25 full-text search (top-100)
     *   - Fusion:     Reciprocal Rank Fusion (k=60) in SQL
     *
     * @param student the student to match jobs for
     * @param limit   maximum number of candidate jobs to return (use 100 for best RRF coverage)
     * @return ranked list of candidate Job entities
     */
    public List<Job> searchJobsForStudent(Student student, int limit) {
        Resume resume = resumeRepository.findByStudentIdAndIsCurrentTrue(student.getId())
                .orElse(null);

        // Build a rich BM25 keyword string from multiple sources for high recall
        String keywords = buildKeywordString(student, resume);

        if (resume == null || resume.getEmbedding() == null) {
            log.warn("No active resume or embedding found for student: {}. Falling back to FTS-only search.", student.getId());

            if (keywords.isBlank()) {
                log.warn("Student {} has no skills or resume text — returning empty job list.", student.getId());
                return Collections.emptyList();
            }

            // Dense leg: zero vector (pgvector will still rank by BM25 via RRF)
            float[] zeroVector = new float[384];
            log.info("FTS-only hybrid search for student {} with keywords: '{}'", student.getId(), keywords);
            return jobRepository.searchHybrid(zeroVector, keywords, limit);
        }

        log.info("Performing hybrid RRF search (top {}) for student {} | keywords: '{}'",
                limit, student.getId(), keywords);
        return jobRepository.searchHybrid(resume.getEmbedding(), keywords, limit);
    }

    /**
     * Builds a rich BM25 query keyword string combining:
     * 1. Skill entity names (most precise)
     * 2. Job titles from experience (domain context)
     * 3. Lead tokens from parsed resume text (broad recall)
     *
     * The resulting string is passed directly to plainto_tsquery() in PostgreSQL.
     */
    private String buildKeywordString(Student student, Resume resume) {
        StringBuilder sb = new StringBuilder();

        // Source 1: explicit skill tags
        if (student.getSkills() != null && !student.getSkills().isEmpty()) {
            String skills = student.getSkills().stream()
                    .map(StudentSkill::getName)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(" "));
            sb.append(skills);
        }

        // Source 2: experience job titles (adds domain context like "backend", "ML engineer")
        if (student.getExperience() != null && !student.getExperience().isEmpty()) {
            String titles = student.getExperience().stream()
                    .map(e -> e.getJobTitle() != null ? e.getJobTitle() : "")
                    .filter(t -> !t.isBlank())
                    .collect(Collectors.joining(" "));
            if (!titles.isBlank()) {
                sb.append(" ").append(titles);
            }
        }

        // Source 3: first 512 chars of parsed resume text (highest raw recall)
        if (resume != null && resume.getParsedText() != null && !resume.getParsedText().isBlank()) {
            String resumeSnippet = resume.getParsedText()
                    .replaceAll("[^a-zA-Z0-9 ]", " ") // remove special chars
                    .replaceAll("\\s+", " ")
                    .trim();
            if (resumeSnippet.length() > 512) {
                resumeSnippet = resumeSnippet.substring(0, 512);
            }
            if (!resumeSnippet.isBlank()) {
                sb.append(" ").append(resumeSnippet);
            }
        }

        String result = sb.toString().trim();

        // Final fallback if all sources are empty
        if (result.isBlank()) {
            result = student.getBio() != null && !student.getBio().isBlank()
                    ? student.getBio()
                    : "software developer engineer";
        }

        return result;
    }
}
