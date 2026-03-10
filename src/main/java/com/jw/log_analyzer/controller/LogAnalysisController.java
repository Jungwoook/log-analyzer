package com.jw.log_analyzer.controller;

import com.jw.log_analyzer.dto.AnalysisResultDto;
import com.jw.log_analyzer.service.LogAnalysisService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class LogAnalysisController {

    private final LogAnalysisService service;

    public LogAnalysisController(LogAnalysisService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalysisResultDto analyze(@RequestPart("file") MultipartFile file) {
        return service.analyze(file);
    }
}
