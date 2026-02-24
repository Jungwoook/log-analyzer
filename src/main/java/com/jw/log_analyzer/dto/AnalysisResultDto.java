package com.jw.log_analyzer.dto;

import java.util.List;
import java.util.Map;

public class AnalysisResultDto {
    private final String mostCalledApiKey;
    private final List<Map.Entry<String, Long>> top3Services;
    private final Map<String, Double> browserRatio;

    public AnalysisResultDto(String mostCalledApiKey, List<Map.Entry<String, Long>> top3Services, Map<String, Double> browserRatio) {
        this.mostCalledApiKey = mostCalledApiKey;
        this.top3Services = top3Services;
        this.browserRatio = browserRatio;
    }

    public String getMostCalledApiKey() { return mostCalledApiKey; }
    public List<Map.Entry<String, Long>> getTop3Services() { return top3Services; }
    public Map<String, Double> getBrowserRatio() { return browserRatio; }
}