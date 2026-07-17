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

    public List<Job> searchJobsForStudent(Student student, int limit) {
        Resume resume = resumeRepository.findByStudentIdAndIsCurrentTrue(student.getId())
                .orElse(null);

        if (resume == null || resume.getEmbedding() == null) {
            log.warn("No active resume or embedding found for student: {}", student.getId());
            
            // Fallback to FTS using skills
            String keywords = student.getSkills().stream()
                    .map(StudentSkill::getName)
                    .collect(Collectors.joining(" "));
            
            if (keywords.isBlank()) {
                return Collections.emptyList();
            }
            
            float[] zeroVector = new float[384];
            return jobRepository.searchHybrid(zeroVector, keywords, limit);
        }

        // Prepare keywords for FTS
        String keywords = student.getSkills().stream()
                .map(StudentSkill::getName)
                .collect(Collectors.joining(" "));

        if (keywords.isBlank()) {
            keywords = student.getBio() != null ? student.getBio() : "";
        }
        if (keywords.isBlank()) {
            keywords = "software developer java";
        }

        log.info("Performing hybrid RRF search for student: {} using keywords: '{}'", student.getId(), keywords);
        return jobRepository.searchHybrid(resume.getEmbedding(), keywords, limit);
    }
}
