package com.jw.log_analyzer.analysis;

import com.jw.log_analyzer.dto.LogEntryDto;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class BrowserRatioCalculator {

    public Map<String, Double> calculate(Collection<LogEntryDto> logEntries) {
        Map<String, Long> browserCounts = logEntries.stream()
                .map(LogEntryDto::getBrowser)
                .filter(this::hasText)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        long totalBrowserCount = browserCounts.values().stream()
                .mapToLong(Long::longValue)
                .sum();

        Map<String, Double> browserRatio = new LinkedHashMap<>();
        if (totalBrowserCount == 0) {
            return browserRatio;
        }

        browserCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> browserRatio.put(entry.getKey(), (entry.getValue() * 100.0) / totalBrowserCount));
        return browserRatio;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
