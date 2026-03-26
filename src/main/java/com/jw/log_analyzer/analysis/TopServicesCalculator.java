package com.jw.log_analyzer.analysis;

import com.jw.log_analyzer.dto.AnalysisResultDto.TopServiceDto;
import com.jw.log_analyzer.dto.LogEntryDto;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;

@Component
public class TopServicesCalculator {

    public List<TopServiceDto> calculate(Collection<LogEntryDto> logEntries) {
        return logEntries.stream()
                .map(LogEntryDto::getServiceId)
                .filter(this::hasText)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
                .map(entry -> new TopServiceDto(entry.getKey(), entry.getValue()))
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
