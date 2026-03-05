package com.jw.log_analyzer.controller;

import com.jw.log_analyzer.service.LogAnalysisService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
public class LogAnalysisController {

    private static final String MAVER_LOG_RESOURCE = "logs/maver.log";
    private static final String MAVER_OUTPUT_PREFIX = "maver-result-";

    private final LogAnalysisService service;

    public LogAnalysisController(LogAnalysisService service) {
        this.service = service;
    }

    @GetMapping("/api/analyze")
    public ResponseEntity<Resource> analyzeKokoa() {
        return buildDownloadResponse(service.analyzeAndWriteToFile());
    }

    @GetMapping("/api/analyze/maver")
    public ResponseEntity<Resource> analyzeMaver() {
        return buildDownloadResponse(service.analyzeAndWriteToFile(MAVER_LOG_RESOURCE, MAVER_OUTPUT_PREFIX));
    }

    private ResponseEntity<Resource> buildDownloadResponse(Path outputPath) {
        if (!outputPath.toFile().exists()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        FileSystemResource resource = new FileSystemResource(outputPath.toFile());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(outputPath.getFileName().toString()).build());
        headers.add(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
        return ResponseEntity.ok().headers(headers).body(resource);
    }
}
