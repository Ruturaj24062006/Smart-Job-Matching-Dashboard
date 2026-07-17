package com.careermatch.backend.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    @Value("${app.resend.apiKey}")
    private String apiKey;

    @Value("${app.resend.from}")
    private String fromEmail;

    private final RestTemplate restTemplate;

    public void sendEmail(String to, String subject, String bodyHtml) {
        if ("mock-resend-key".equals(apiKey) || apiKey.isBlank()) {
            log.warn("Resend API Key is not set. Mock sending email to: {} - Subject: {}", to, subject);
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("from", fromEmail);
            requestBody.put("to", to);
            requestBody.put("subject", subject);
            requestBody.put("html", bodyHtml);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity("https://api.resend.com/emails", entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Email dispatched successfully to: {}", to);
            } else {
                log.error("Resend API returned non-success code: {}. Response: {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to send email via Resend API to: {}. Error: {}", to, e.getMessage());
        }
    }
}
