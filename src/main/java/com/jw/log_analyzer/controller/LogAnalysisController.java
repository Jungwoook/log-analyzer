package com.jw.log_analyzer.controller;

import com.jw.log_analyzer.service.LogAnalysisService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
public class LogAnalysisController {

    private final LogAnalysisService service;

    public LogAnalysisController(LogAnalysisService service) {
        this.service = service;
    }

    @GetMapping("/api/analyze")
    public ResponseEntity<FileSystemResource> analyze() {
        service.analyzeAndWrite();
        Path p = service.getResultPath();
        if (!p.toFile().exists()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        FileSystemResource resource = new FileSystemResource(p.toFile());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename("kokoa-result.txt").build());
        headers.add(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
        return ResponseEntity.ok().headers(headers).body(resource);
    }
}