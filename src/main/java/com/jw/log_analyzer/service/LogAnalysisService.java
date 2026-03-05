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
import java.util.stream.Stream;

@Service
public class LogAnalysisService {

    private static final Path OUTPUT_DIR = Path.of("logs");
    private static final String DEFAULT_LOG_RESOURCE = "logs/kokoa.txt";
    private static final String DEFAULT_OUTPUT_PREFIX = "kokoa-result-";
    private final LogRepository repository;

    public LogAnalysisService(LogRepository repository) {
        this.repository = repository;
    }

    public AnalysisResultDto analyzeAndWrite() {
        AnalysisResultDto dto = analyze(DEFAULT_LOG_RESOURCE);
        Path output = createOutputPath(DEFAULT_OUTPUT_PREFIX);
        writeResultFile(dto, output);
        return dto;
    }

    public Path analyzeAndWriteToFile() {
        return analyzeAndWriteToFile(DEFAULT_LOG_RESOURCE, DEFAULT_OUTPUT_PREFIX);
    }

    public Path analyzeAndWriteToFile(String logResourcePath, String outputPrefix) {
        AnalysisResultDto dto = analyze(logResourcePath);
        Path output = createOutputPath(outputPrefix);
        writeResultFile(dto, output);
        return output;
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

        List<Map.Entry<String, Long>> top3Services = serviceCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
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

    private Path createOutputPath(String outputPrefix) {
        String fileName = outputPrefix + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".txt";
        return OUTPUT_DIR.resolve(fileName);
    }

    private void writeResultFile(AnalysisResultDto dto, Path output) {
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
            Files.createDirectories(OUTPUT_DIR);
            Files.write(output, lines, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write result file", e);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
