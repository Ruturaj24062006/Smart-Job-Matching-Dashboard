package com.careermatch.backend.matching.service;

import com.careermatch.backend.ai.service.GroqService;
import com.careermatch.backend.exception.ResourceNotFoundException;
import com.careermatch.backend.job.entity.Job;
import com.careermatch.backend.matching.entity.Match;
import com.careermatch.backend.matching.repository.MatchRepository;
import com.careermatch.backend.student.entity.Student;
import com.careermatch.backend.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingService {

    private final StudentRepository studentRepository;
    private final MatchRepository matchRepository;
    private final SearchService searchService;
    private final ScoringService scoringService;
    private final GroqService groqService;

    @Transactional
    public List<Match> generateMatchesForStudent(UUID studentId) {
        log.info("Generating job matches for student: {}", studentId);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));

        // 1. Search top 20 jobs using hybrid search
        List<Job> topJobs = searchService.searchJobsForStudent(student, 20);
        List<Match> matches = new ArrayList<>();

        for (Job job : topJobs) {
            matches.add(generateMatchForStudentAndJob(student, job));
        }

        log.info("Successfully updated {} matches for student {}", matches.size(), studentId);
        return matches;
    }

    @Transactional
    public Match generateMatchesForStudentAndJob(UUID studentId, Job job) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));
        return generateMatchForStudentAndJob(student, job);
    }

    @Transactional
    public Match generateMatchForStudentAndJob(Student student, Job job) {
        // Calculate composite scores
        double score = scoringService.calculateCompositeScore(student, job);
        boolean eligible = score >= 40.0; // threshold for eligibility

        // Save or update match
        Optional<Match> existingOpt = matchRepository.findByStudentIdAndJobId(student.getId(), job.getId());
        Match match;
        if (existingOpt.isPresent()) {
            match = existingOpt.get();
            match.setCompositeScore(score);
            match.setEligibilityStatus(eligible);
        } else {
            match = Match.builder()
                    .student(student)
                    .job(job)
                    .compositeScore(score)
                    .eligibilityStatus(eligible)
                    .build();
        }
        return matchRepository.save(match);
    }

    @Transactional
    public Match enrichMatchWithAi(UUID matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchId));

        // Check if already enriched
        if (match.getExplanation() != null && match.getSkillGap() != null) {
            return match;
        }

        log.info("Enriching Match: {} with AI insights...", matchId);
        
        StringBuilder profileSummary = new StringBuilder();
        profileSummary.append("Skills: ");
        match.getStudent().getSkills().forEach(s -> profileSummary.append(s.getName()).append(", "));
        profileSummary.append("\nBio: ").append(match.getStudent().getBio());
        profileSummary.append("\nExperience: ");
        match.getStudent().getExperience().forEach(e -> profileSummary.append(e.getJobTitle()).append(" at ").append(e.getCompanyName()).append(". "));

        String jobDesc = match.getJob().getTitle() + "\n" + match.getJob().getDescription() + "\n" + match.getJob().getRequirements();

        // Call AI Groq explanations
        String explanation = groqService.explainMatch(profileSummary.toString(), jobDesc);
        String skillGapJson = groqService.explainSkillGap(profileSummary.toString(), jobDesc);

        match.setExplanation(explanation);
        match.setSkillGap(skillGapJson);
        match.setCareerInsights("Focus on bridging the skill gap to increase your eligibility rating.");

        return matchRepository.save(match);
    }

    public List<Match> getMatchesForStudent(UUID studentId) {
        return matchRepository.findByStudentIdOrderByCompositeScoreDesc(studentId);
    }
}
