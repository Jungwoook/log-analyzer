package com.jw.log_analyzer.controller;

import com.jw.log_analyzer.dto.AnalysisResultDto;
import com.jw.log_analyzer.service.LogAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class LogAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(LogAnalysisController.class);

    private final LogAnalysisService service;

    public LogAnalysisController(LogAnalysisService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalysisResultDto analyze(@RequestPart("file") MultipartFile file) {
        log.info("Log analysis request started. fileName={}, fileSize={} bytes", file.getOriginalFilename(), file.getSize());
        return service.analyze(file);
    }
}
