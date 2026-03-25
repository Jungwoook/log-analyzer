package com.jw.log_analyzer.analysis;

import com.jw.log_analyzer.dto.AnalysisResultDto;
import com.jw.log_analyzer.dto.AnalysisResultDto.TopServiceDto;
import com.jw.log_analyzer.dto.LogEntryDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogAnalysisResultAssemblerTest {

    private final LogAnalysisResultAssembler resultAssembler = new LogAnalysisResultAssembler(
            new MostCalledApiKeyCalculator(),
            new TopServicesCalculator(),
            new BrowserRatioCalculator()
    );

    @Test
    void assembleBuildsExistingResponseShapeFromParsedLogs() {
        AnalysisResultDto result = resultAssembler.assemble(List.of(
                entry("beta", "news", "Chrome"),
                entry("beta", "news", "Chrome"),
                entry("alpha", "book", "Firefox"),
                entry("alpha", "book", "Firefox"),
                entry(null, "map", null)
        ));

        assertThat(result.getMostCalledApiKey()).isEqualTo("alpha");
        assertThat(result.getTop3Services()).containsExactly(
                new TopServiceDto("book", 2L),
                new TopServiceDto("news", 2L),
                new TopServiceDto("map", 1L)
        );
        assertThat(result.getBrowserRatio()).containsExactlyEntriesOf(result.getBrowserRatio());
        assertThat(result.getBrowserRatio().keySet()).containsExactly("Chrome", "Firefox");
        assertThat(result.getBrowserRatio().get("Chrome")).isEqualTo(50.0);
        assertThat(result.getBrowserRatio().get("Firefox")).isEqualTo(50.0);
    }

    private LogEntryDto entry(String apiKey, String serviceId, String browser) {
        return new LogEntryDto(
                200,
                "http://apis.kokoa.com/search/" + serviceId + "?apikey=" + apiKey,
                serviceId,
                apiKey,
                browser,
                LocalDateTime.now()
        );
    }
}
