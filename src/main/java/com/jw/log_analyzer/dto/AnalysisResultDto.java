package com.jw.log_analyzer.dto;

import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
public class AnalysisResultDto {
    String mostCalledApiKey;
    List<TopServiceDto> top3Services;
    Map<String, Double> browserRatio;

    public record TopServiceDto(String serviceId, long count) {
    }
}
