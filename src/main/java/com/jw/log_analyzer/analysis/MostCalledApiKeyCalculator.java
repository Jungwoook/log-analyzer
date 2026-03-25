package com.jw.log_analyzer.analysis;

import com.jw.log_analyzer.dto.LogEntryDto;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MostCalledApiKeyCalculator {

    public String calculate(Collection<LogEntryDto> logEntries) {
        return logEntries.stream()
                .map(LogEntryDto::getApiKey)
                .filter(this::hasText)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.<String, Long>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
