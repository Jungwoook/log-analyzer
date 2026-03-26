package com.jw.log_analyzer.analysis;

import com.jw.log_analyzer.dto.AnalysisResultDto;
import com.jw.log_analyzer.dto.LogEntryDto;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class LogAnalysisResultAssembler {

    private final MostCalledApiKeyCalculator mostCalledApiKeyCalculator;
    private final TopServicesCalculator topServicesCalculator;
    private final BrowserRatioCalculator browserRatioCalculator;

    public LogAnalysisResultAssembler(
            MostCalledApiKeyCalculator mostCalledApiKeyCalculator,
            TopServicesCalculator topServicesCalculator,
            BrowserRatioCalculator browserRatioCalculator
    ) {
        this.mostCalledApiKeyCalculator = mostCalledApiKeyCalculator;
        this.topServicesCalculator = topServicesCalculator;
        this.browserRatioCalculator = browserRatioCalculator;
    }

    public AnalysisResultDto assemble(Collection<LogEntryDto> logEntries) {
        return new AnalysisResultDto(
                mostCalledApiKeyCalculator.calculate(logEntries),
                topServicesCalculator.calculate(logEntries),
                browserRatioCalculator.calculate(logEntries)
        );
    }
}
