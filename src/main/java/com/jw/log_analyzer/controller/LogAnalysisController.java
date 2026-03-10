package com.jw.log_analyzer.controller;

import com.jw.log_analyzer.dto.AnalysisResultDto;
import com.jw.log_analyzer.service.LogAnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogAnalysisController {

    private static final String MAVER_LOG_RESOURCE = "logs/maver.log";

    private final LogAnalysisService service;

    public LogAnalysisController(LogAnalysisService service) {
        this.service = service;
    }

    @GetMapping("/api/analyze")
    public AnalysisResultDto analyzeKokoa() {
        return service.analyze();
    }

    @GetMapping("/api/analyze/maver")
    public AnalysisResultDto analyzeMaver() {
        return service.analyze(MAVER_LOG_RESOURCE);
    }
}
