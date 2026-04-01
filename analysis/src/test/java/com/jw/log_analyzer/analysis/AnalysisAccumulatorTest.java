package com.jw.log_analyzer.analysis;

import com.jw.log_analyzer.parser.contract.LogRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisAccumulatorTest {

    @Test
    void toAnalysisResultBuildsExistingResponseShapeFromParsedLogs() {
        AnalysisAccumulator accumulator = new AnalysisAccumulator();

        accumulator.accept(entry("beta", "news", "Chrome"));
        accumulator.accept(entry("beta", "news", "Chrome"));
        accumulator.accept(entry("alpha", "book", "Firefox"));
        accumulator.accept(entry("alpha", "book", "Firefox"));
        accumulator.accept(entry(null, "map", null));

        AnalysisResult result = accumulator.toAnalysisResult();

        assertThat(accumulator.processedRecordCount()).isEqualTo(5L);
        assertThat(result.mostCalledApiKey()).isEqualTo("alpha");
        assertThat(result.top3Services()).containsExactly(
                new TopServiceCount("book", 2L),
                new TopServiceCount("news", 2L),
                new TopServiceCount("map", 1L)
        );
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
