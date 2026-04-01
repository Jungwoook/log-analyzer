package com.jw.log_analyzer.analysis;

import com.jw.log_analyzer.parser.contract.LogRecord;
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
        AnalysisResult result = resultAssembler.assemble(List.of(
                entry("beta", "news", "Chrome"),
                entry("beta", "news", "Chrome"),
                entry("alpha", "book", "Firefox"),
                entry("alpha", "book", "Firefox"),
                entry(null, "map", null)
        ));

        assertThat(result.mostCalledApiKey()).isEqualTo("alpha");
        assertThat(result.top3Services()).containsExactly(
                new TopServiceCount("book", 2L),
                new TopServiceCount("news", 2L),
                new TopServiceCount("map", 1L)
        );
        assertThat(result.browserRatio()).containsExactlyEntriesOf(result.browserRatio());
        assertThat(result.browserRatio().keySet()).containsExactly("Chrome", "Firefox");
        assertThat(result.browserRatio().get("Chrome")).isEqualTo(50.0);
        assertThat(result.browserRatio().get("Firefox")).isEqualTo(50.0);
    }

    private LogRecord entry(String apiKey, String serviceId, String browser) {
        return new LogRecord(
                200,
                "http://apis.kokoa.com/search/" + serviceId + "?apikey=" + apiKey,
                serviceId,
                apiKey,
                browser,
                LocalDateTime.now()
        );
    }
}
