package com.jw.log_analyzer.service;

import com.jw.log_analyzer.analysis.AnalysisResult;
import com.jw.log_analyzer.analysis.LogAnalysisResultAssembler;
import com.jw.log_analyzer.dto.AnalysisResultDto;
import com.jw.log_analyzer.parser.contract.LogRecord;
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
    private final AnalysisResultDtoMapper analysisResultDtoMapper;

    public AnalysisResultDto analyze(MultipartFile file) {
        long startTime = System.currentTimeMillis();
        String fileName = file.getOriginalFilename();
        log.info("Analysis started. fileName={}", fileName);

        try (Stream<LogRecord> logs = repository.streamLogs(file)) {
            log.info("File parsing started. fileName={}", fileName);
            List<LogRecord> parsedEntries = logs.toList();
            log.info("File parsing completed. fileName={}, parsedEntries={}", fileName, parsedEntries.size());

            log.info("Statistics calculation started. fileName={}", fileName);
            AnalysisResult analysisResult = resultAssembler.assemble(parsedEntries);
            AnalysisResultDto result = analysisResultDtoMapper.toDto(analysisResult);
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
