package com.jw.log_analyzer.service;

import com.jw.log_analyzer.dto.AnalysisResultDto;
import com.jw.log_analyzer.dto.LogEntryDto;
import com.jw.log_analyzer.repository.LogRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LogAnalysisService {

    private final LogRepository repository;
    private static final Path OUTPUT = Path.of("src/main/resources/logs/kokoa-result.txt");

    public LogAnalysisService(LogRepository repository) {
        this.repository = repository;
    }

    public AnalysisResultDto analyzeAndWrite() {
        List<LogEntryDto> logs = repository.readAllLogs();

        // apikey counts
        Map<String, Long> apiKeyCounts = logs.stream()
                .map(LogEntryDto::getApiKey)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(k -> k, Collectors.counting()));

        String mostCalledApiKey = apiKeyCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        // service counts
        Map<String, Long> serviceCounts = logs.stream()
                .map(LogEntryDto::getApiService)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        List<Map.Entry<String, Long>> top3Services = serviceCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .collect(Collectors.toList());

        // browser ratio
        Map<String, Long> browserCounts = logs.stream()
                .map(LogEntryDto::getBrowser)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(b -> b, Collectors.counting()));

        long totalBrowser = browserCounts.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Double> browserRatio = new LinkedHashMap<>();
        if (totalBrowser > 0) {
            browserCounts.forEach((b, c) -> browserRatio.put(b, (c * 100.0) / totalBrowser));
        }

        AnalysisResultDto dto = new AnalysisResultDto(mostCalledApiKey, top3Services, browserRatio);
        writeResultFile(dto);
        return dto;
    }

    private void writeResultFile(AnalysisResultDto dto) {
        List<String> lines = new ArrayList<>();
        lines.add("Most called APIKEY: " + (dto.getMostCalledApiKey() == null ? "NONE" : dto.getMostCalledApiKey()));
        lines.add("");
        lines.add("Top 3 API Services (by calls):");
        int rank = 1;
        for (Map.Entry<String, Long> e : dto.getTop3Services()) {
            lines.add(String.format("%d. %s -> %d", rank++, e.getKey(), e.getValue()));
        }
        lines.add("");
        lines.add("Browser usage ratio (%):");
        dto.getBrowserRatio().forEach((k, v) -> lines.add(String.format("%s: %.2f%%", k, v)));

        try {
            Files.createDirectories(OUTPUT.getParent());
            Files.write(OUTPUT, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write result file", e);
        }
    }

    public Path getResultPath() {
        return OUTPUT;
    }
}