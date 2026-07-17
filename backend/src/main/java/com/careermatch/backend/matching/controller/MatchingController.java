package com.careermatch.backend.matching.controller;

import com.careermatch.backend.auth.entity.User;
import com.careermatch.backend.auth.repository.UserRepository;
import com.careermatch.backend.common.ApiResponse;
import com.careermatch.backend.exception.BadRequestException;
import com.careermatch.backend.matching.dto.MatchDetailsResponse;
import com.careermatch.backend.matching.dto.MatchResponse;
import com.careermatch.backend.matching.entity.Match;
import com.careermatch.backend.matching.service.MatchingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
@Tag(name = "Job Matching Engine", description = "Endpoints for retrieving AI matches, scoring, and job eligibility explanations")
public class MatchingController {

    private final MatchingService matchingService;
    private final UserRepository userRepository;

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    @Operation(summary = "Re-trigger the scoring and matching engine to generate top jobs list")
    public ResponseEntity<ApiResponse<String>> generateMatches() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));

        matchingService.generateMatchesForStudent(user.getId());
        return ResponseEntity.ok(ApiResponse.success("Scoring and match updates triggered successfully", "Generated"));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    @Operation(summary = "Retrieve all matches for the logged-in student")
    public ResponseEntity<ApiResponse<List<MatchResponse>>> getMatches() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));

        List<Match> matches = matchingService.getMatchesForStudent(user.getId());
        List<MatchResponse> response = matches.stream()
                .map(m -> MatchResponse.builder()
                        .id(m.getId())
                        .jobId(m.getJob().getId())
                        .jobTitle(m.getJob().getTitle())
                        .companyName(m.getJob().getCompany().getName())
                        .location(m.getJob().getLocation())
                        .compositeScore(m.getCompositeScore())
                        .eligibilityStatus(m.isEligibilityStatus())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{matchId}/details")
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    @Operation(summary = "Fetch detailed explanation and AI insights for a specific match")
    public ResponseEntity<ApiResponse<MatchDetailsResponse>> getMatchDetails(@PathVariable("matchId") UUID matchId) {
        Match enriched = matchingService.enrichMatchWithAi(matchId);
        
        MatchDetailsResponse response = MatchDetailsResponse.builder()
                .id(enriched.getId())
                .jobId(enriched.getJob().getId())
                .jobTitle(enriched.getJob().getTitle())
                .companyName(enriched.getJob().getCompany().getName())
                .location(enriched.getJob().getLocation())
                .jobDescription(enriched.getJob().getDescription())
                .jobRequirements(enriched.getJob().getRequirements())
                .compositeScore(enriched.getCompositeScore())
                .eligibilityStatus(enriched.isEligibilityStatus())
                .explanation(enriched.getExplanation())
                .skillGap(enriched.getSkillGap())
                .careerInsights(enriched.getCareerInsights())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
