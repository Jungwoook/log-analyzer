package com.jw.log_analyzer.dto;

import java.util.List;

public class AnalysisResultDto {
    private final String mostCalledApiKey;
    private final List<TopServiceDto> top3Services;
    private final java.util.Map<String, Double> browserRatio;

    public AnalysisResultDto(String mostCalledApiKey, List<TopServiceDto> top3Services, java.util.Map<String, Double> browserRatio) {
        this.mostCalledApiKey = mostCalledApiKey;
        this.top3Services = top3Services;
        this.browserRatio = browserRatio;
    }

    public String getMostCalledApiKey() { return mostCalledApiKey; }
    public List<TopServiceDto> getTop3Services() { return top3Services; }
    public java.util.Map<String, Double> getBrowserRatio() { return browserRatio; }

    public record TopServiceDto(String serviceId, long count) {
    }
}
