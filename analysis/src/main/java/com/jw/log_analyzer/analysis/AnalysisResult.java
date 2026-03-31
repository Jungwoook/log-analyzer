package com.jw.log_analyzer.analysis;

import java.util.List;
import java.util.Map;

public record AnalysisResult(
        String mostCalledApiKey,
        List<TopServiceCount> top3Services,
        Map<String, Double> browserRatio
) {
}
