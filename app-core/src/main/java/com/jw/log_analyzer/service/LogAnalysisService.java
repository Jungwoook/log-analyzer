package com.jw.log_analyzer.service;

import com.jw.log_analyzer.analysis.AnalysisAccumulator;
import com.jw.log_analyzer.analysis.AnalysisResult;
import com.jw.log_analyzer.dto.AnalysisResultDto;
import com.jw.log_analyzer.parser.contract.LogRecord;
import com.jw.log_analyzer.repository.LogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogAnalysisService {

    private final LogRepository repository;
    private final AnalysisResultDtoMapper analysisResultDtoMapper;

    public AnalysisResultDto analyze(MultipartFile file) {
        long startTime = System.currentTimeMillis();
        String fileName = file.getOriginalFilename();
        log.info("Analysis started. fileName={}", fileName);
        AnalysisAccumulator accumulator = new AnalysisAccumulator();

        try (Stream<LogRecord> logs = repository.streamLogs(file)) {
            log.info("File parsing started. fileName={}", fileName);
            logs.forEach(accumulator::accept);
            log.info("File parsing completed. fileName={}, processedRecordCount={}",
                    fileName, accumulator.processedRecordCount());

            log.info("Statistics calculation started. fileName={}", fileName);
            AnalysisResult analysisResult = accumulator.toAnalysisResult();
            AnalysisResultDto result = analysisResultDtoMapper.toDto(analysisResult);
            log.info("Statistics calculation completed. fileName={}", fileName);
            long durationMs = System.currentTimeMillis() - startTime;
            log.info("Analysis completed. fileName={}, durationMs={}, processedRecordCount={}",
                    fileName, durationMs, accumulator.processedRecordCount());
            return result;
        }
    }
}
