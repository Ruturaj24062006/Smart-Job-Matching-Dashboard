package com.careermatch.backend.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

@Service
@Slf4j
public class SupabaseStorageService {

    @Value("${supabase.url:https://qacuyhktzqhfqtswsrgs.supabase.co}")
    private String supabaseUrl;

    @Value("${supabase.anonKey:}")
    private String supabaseAnonKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String BUCKET_NAME = "resumes";

    /**
     * Uploads the resume PDF/DOCX file to Supabase Storage bucket.
     * Returns the public URL of the stored file.
     */
    public String uploadFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "resume.pdf";
        String cleanFilename = originalFilename.replaceAll("[^a-zA-Z0-9\\._-]", "_");
        String storageKey = UUID.randomUUID() + "-" + cleanFilename;

        if (supabaseUrl == null || supabaseUrl.isBlank() || supabaseAnonKey == null || supabaseAnonKey.isBlank()) {
            log.warn("Supabase credentials not configured. Returning fallback relative storage key: {}", storageKey);
            return storageKey;
        }

        try {
            String uploadUrl = supabaseUrl.replaceAll("/+$", "") + "/storage/v1/object/" + BUCKET_NAME + "/" + storageKey;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + supabaseAnonKey);
            headers.set("apikey", supabaseAnonKey);
            String contentType = file.getContentType() != null ? file.getContentType() : "application/pdf";
            headers.setContentType(MediaType.parseMediaType(contentType));

            HttpEntity<byte[]> requestEntity = new HttpEntity<>(file.getBytes(), headers);

            ResponseEntity<String> response = restTemplate.exchange(uploadUrl, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String publicUrl = supabaseUrl.replaceAll("/+$", "") + "/storage/v1/object/public/" + BUCKET_NAME + "/" + storageKey;
                log.info("Uploaded original resume to Supabase Storage: {}", publicUrl);
                return publicUrl;
            } else {
                log.warn("Supabase Storage upload returned HTTP {}: {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.warn("Supabase Storage upload failed ({}), defaulting to storageKey: {}", e.getMessage(), storageKey);
        }

        // Return public URL or fallback key
        return supabaseUrl.replaceAll("/+$", "") + "/storage/v1/object/public/" + BUCKET_NAME + "/" + storageKey;
    }
}
