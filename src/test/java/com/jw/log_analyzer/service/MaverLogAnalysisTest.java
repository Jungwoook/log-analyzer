package com.jw.log_analyzer.service;

import com.jw.log_analyzer.dto.AnalysisResultDto;
import com.jw.log_analyzer.repository.LogRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MaverLogAnalysisTest {

    @Test
    void maverJsonLogProducesExpectedStatistics() {
        LogAnalysisService service = new LogAnalysisService(new LogRepository());

        AnalysisResultDto result = service.analyze("logs/maver.log");

        assertThat(result.getMostCalledApiKey()).isEqualTo("a1b2");
        assertThat(result.getTop3Services())
                .containsExactly(
                        Map.entry("news", 2L),
                        Map.entry("book", 1L),
                        Map.entry("image", 1L)
                );
        assertThat(result.getBrowserRatio().keySet())
                .containsExactly("Chrome", "Firefox", "IE", "Safari");
    }
}
