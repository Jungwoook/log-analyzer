package com.jw.log_analyzer.service;

import com.jw.log_analyzer.dto.AnalysisResultDto;
import com.jw.log_analyzer.dto.AnalysisResultDto.TopServiceDto;
import com.jw.log_analyzer.dto.LogEntryDto;
import com.jw.log_analyzer.repository.LogRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Stream;

@Service
public class LogAnalysisService {

    private static final String DEFAULT_LOG_RESOURCE = "logs/kokoa.txt";
    private final LogRepository repository;

    public LogAnalysisService(LogRepository repository) {
        this.repository = repository;
    }

    public AnalysisResultDto analyze() {
        return analyze(DEFAULT_LOG_RESOURCE);
    }

    public AnalysisResultDto analyze(String logResourcePath) {
        Map<String, Long> apiKeyCounts = new HashMap<>();
        Map<String, Long> serviceCounts = new HashMap<>();
        Map<String, Long> browserCounts = new HashMap<>();
        long totalBrowser = 0L;

        try (Stream<LogEntryDto> logs = repository.streamLogs(logResourcePath)) {
            Iterator<LogEntryDto> iterator = logs.iterator();
            while (iterator.hasNext()) {
                LogEntryDto log = iterator.next();
                if (hasText(log.getApiKey())) {
                    apiKeyCounts.merge(log.getApiKey(), 1L, Long::sum);
                }
                if (hasText(log.getServiceId())) {
                    serviceCounts.merge(log.getServiceId(), 1L, Long::sum);
                }
                if (hasText(log.getBrowser())) {
                    browserCounts.merge(log.getBrowser(), 1L, Long::sum);
                    totalBrowser++;
                }
            }
        }

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

        return new AnalysisResultDto(mostCalledApiKey, top3Services, browserRatio);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
