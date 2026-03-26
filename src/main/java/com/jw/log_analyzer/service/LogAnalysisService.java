package com.jw.log_analyzer.service;

import com.jw.log_analyzer.analysis.LogAnalysisResultAssembler;
import com.jw.log_analyzer.dto.AnalysisResultDto;
import com.jw.log_analyzer.dto.LogEntryDto;
import com.jw.log_analyzer.repository.LogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogAnalysisService {

    private final LogRepository repository;
    private final LogAnalysisResultAssembler resultAssembler;

    public LogAnalysisService(LogRepository repository, LogAnalysisResultAssembler resultAssembler) {
        this.repository = repository;
        this.resultAssembler = resultAssembler;
    }

    public AnalysisResultDto analyze(MultipartFile file) {
        long startTime = System.currentTimeMillis();
        String fileName = file.getOriginalFilename();
        log.info("Analysis started. fileName={}", fileName);

        try (Stream<LogEntryDto> logs = repository.streamLogs(file)) {
            log.info("File parsing started. fileName={}", fileName);
            List<LogEntryDto> parsedEntries = logs.toList();
            log.info("File parsing completed. fileName={}, parsedEntries={}", fileName, parsedEntries.size());

            log.info("Statistics calculation started. fileName={}", fileName);
            AnalysisResultDto result = resultAssembler.assemble(parsedEntries);
            log.info("Statistics calculation completed. fileName={}", fileName);
            long durationMs = System.currentTimeMillis() - startTime;
            log.info("Analysis completed. fileName={}, durationMs={}", fileName, durationMs);
            return result;
        } catch (RuntimeException e) {
            log.error("Analysis failed. fileName={}", fileName, e);
            throw e;
        }

    }
}
