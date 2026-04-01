package com.jw.log_analyzer.analysis;

import com.jw.log_analyzer.parser.contract.LogRecord;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;

@Component
public class TopServicesCalculator {

    public List<TopServiceCount> calculate(Collection<LogRecord> logEntries) {
        return logEntries.stream()
                .map(LogRecord::getServiceId)
                .filter(this::hasText)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
                .map(entry -> new TopServiceCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
