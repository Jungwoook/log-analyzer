package com.jw.log_analyzer.service;

import com.jw.log_analyzer.dto.AnalysisResultDto;
import com.jw.log_analyzer.dto.AnalysisResultDto.TopServiceDto;
import com.jw.log_analyzer.dto.LogEntryDto;
import com.jw.log_analyzer.repository.LogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Stream;

@Service
public class LogAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(LogAnalysisService.class);

    private final LogRepository repository;

    public LogAnalysisService(LogRepository repository) {
        this.repository = repository;
    }

    public AnalysisResultDto analyze(MultipartFile file) {
        long startTime = System.currentTimeMillis();
        String fileName = file.getOriginalFilename();
        log.info("Analysis started. fileName={}", fileName);

        Map<String, Long> apiKeyCounts = new HashMap<>();
        Map<String, Long> serviceCounts = new HashMap<>();
        Map<String, Long> browserCounts = new HashMap<>();
        long totalBrowser = 0L;
        long parsedCount = 0L;

        try {
            log.info("File parsing started. fileName={}", fileName);
            try (Stream<LogEntryDto> logs = repository.streamLogs(file)) {
                Iterator<LogEntryDto> iterator = logs.iterator();
                while (iterator.hasNext()) {
                    LogEntryDto entry = iterator.next();
                    parsedCount++;
                    if (hasText(entry.getApiKey())) {
                        apiKeyCounts.merge(entry.getApiKey(), 1L, Long::sum);
                    }
                    if (hasText(entry.getServiceId())) {
                        serviceCounts.merge(entry.getServiceId(), 1L, Long::sum);
                    }
                    if (hasText(entry.getBrowser())) {
                        browserCounts.merge(entry.getBrowser(), 1L, Long::sum);
                        totalBrowser++;
                    }
                }
            }
            log.info("File parsing completed. fileName={}, parsedEntries={}", fileName, parsedCount);

            log.info("Statistics calculation started. fileName={}", fileName);
            String mostCalledApiKey = apiKeyCounts.entrySet().stream()
                    .max(Map.Entry.<String, Long>comparingByValue()
                            .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                    .map(Map.Entry::getKey)
                    .orElse(null);

            List<TopServiceDto> top3Services = serviceCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .limit(3)
                    .map(entry -> new TopServiceDto(entry.getKey(), entry.getValue()))
                    .toList();

            Map<String, Double> browserRatio = new LinkedHashMap<>();
            if (totalBrowser > 0) {
                final long totalBrowserCount = totalBrowser;
                browserCounts.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> browserRatio.put(entry.getKey(), (entry.getValue() * 100.0) / totalBrowserCount));
            }

            log.info("Statistics calculation completed. fileName={}", fileName);
            long durationMs = System.currentTimeMillis() - startTime;
            log.info("Analysis completed. fileName={}, durationMs={}", fileName, durationMs);
            return new AnalysisResultDto(mostCalledApiKey, top3Services, browserRatio);
        } catch (RuntimeException e) {
            log.error("Analysis failed. fileName={}", fileName, e);
            throw e;
        }

    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
