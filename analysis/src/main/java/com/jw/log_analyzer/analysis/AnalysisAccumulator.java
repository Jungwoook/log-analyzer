package com.jw.log_analyzer.analysis;

import com.jw.log_analyzer.parser.contract.LogRecord;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnalysisAccumulator {

    private final Map<String, Long> apiKeyCounts = new HashMap<>();
    private final Map<String, Long> serviceCounts = new HashMap<>();
    private final Map<String, Long> browserCounts = new HashMap<>();
    private long processedRecordCount;

    public void accept(LogRecord record) {
        processedRecordCount++;
        increment(apiKeyCounts, record.getApiKey());
        increment(serviceCounts, record.getServiceId());
        increment(browserCounts, record.getBrowser());
    }

    public AnalysisResult toAnalysisResult() {
        return new AnalysisResult(
                mostCalledApiKey(),
                top3Services(),
                browserRatio()
        );
    }

    public long processedRecordCount() {
        return processedRecordCount;
    }

    private void increment(Map<String, Long> counts, String value) {
        if (!hasText(value)) {
            return;
        }
        counts.merge(value, 1L, Long::sum);
    }

    private String mostCalledApiKey() {
        return apiKeyCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private List<TopServiceCount> top3Services() {
        return serviceCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
                .map(entry -> new TopServiceCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Map<String, Double> browserRatio() {
        long totalBrowserCount = browserCounts.values().stream()
                .mapToLong(Long::longValue)
                .sum();

        Map<String, Double> ratioByBrowser = new LinkedHashMap<>();
        if (totalBrowserCount == 0) {
            return ratioByBrowser;
        }

        browserCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> ratioByBrowser.put(
                        entry.getKey(),
                        (entry.getValue() * 100.0) / totalBrowserCount
                ));
        return ratioByBrowser;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
