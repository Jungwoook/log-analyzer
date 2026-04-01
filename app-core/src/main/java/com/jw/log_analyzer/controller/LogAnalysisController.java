package com.jw.log_analyzer.controller;

import com.jw.log_analyzer.dto.AnalysisResultDto;
import com.jw.log_analyzer.service.LogAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequiredArgsConstructor
public class LogAnalysisController {

    private final LogAnalysisService service;

    @PostMapping(value = "/api/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalysisResultDto analyze(@RequestPart("file") MultipartFile file) {
        log.info("Log analysis request started. fileName={}, fileSize={} bytes", file.getOriginalFilename(), file.getSize());
        return service.analyze(file);
    }
}
