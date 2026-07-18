package com.careermatch.backend.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${app.upload.dir}")
    private String uploadDir;

    private Path rootDir;

    @PostConstruct
    public void init() {
        this.rootDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootDir);
            log.info("Initialized upload directory at: {}", this.rootDir);
        } catch (IOException e) {
            log.error("Could not create upload directory: {}", e.getMessage());
            throw new RuntimeException("Could not create upload directory", e);
        }
    }

    public String storeFile(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename() != null 
                ? file.getOriginalFilename().replaceAll("[^a-zA-Z0-9\\._-]", "_") 
                : "resume.pdf";
        String filename = UUID.randomUUID() + "-" + originalName;
        Path targetLocation = this.rootDir.resolve(filename);
        
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
        }
        
        log.info("Saved file locally at: {}", targetLocation);
        return filename; // Return filename as key/relative path
    }

    public InputStream getFileAsStream(String filename) throws IOException {
        Path filePath = this.rootDir.resolve(filename).normalize();
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + filename);
        }
        return Files.newInputStream(filePath);
    }
}
