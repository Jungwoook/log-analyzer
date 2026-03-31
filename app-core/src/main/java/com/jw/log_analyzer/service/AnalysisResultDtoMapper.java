package com.jw.log_analyzer.service;

import com.jw.log_analyzer.analysis.AnalysisResult;
import com.jw.log_analyzer.dto.AnalysisResultDto;
import org.springframework.stereotype.Component;

@Component
public class AnalysisResultDtoMapper {

    public AnalysisResultDto toDto(AnalysisResult analysisResult) {
        return new AnalysisResultDto(
                analysisResult.mostCalledApiKey(),
                analysisResult.top3Services().stream()
                        .map(service -> new AnalysisResultDto.TopServiceDto(service.serviceId(), service.count()))
                        .toList(),
                analysisResult.browserRatio()
        );
    }
}
