package com.jw.log_analyzer.service;

import com.jw.log_analyzer.dto.AnalysisResultDto;
import com.jw.log_analyzer.dto.AnalysisResultDto.TopServiceDto;
import com.jw.log_analyzer.dto.LogEntryDto;
import com.jw.log_analyzer.repository.LogRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogAnalysisServiceTest {

    @Test
    void analyzeChoosesLexicographicallySmallestOnCountTie() {
        LogRepository repository = mock(LogRepository.class);
        when(repository.streamLogs(anyString())).thenReturn(Stream.of(
                entry("beta", "news", "Chrome"),
                entry("beta", "news", "Chrome"),
                entry("alpha", "book", "Firefox"),
                entry("alpha", "book", "Firefox")
        ));

        LogAnalysisService service = new LogAnalysisService(repository);

        AnalysisResultDto result = service.analyze();

        assertThat(result.getMostCalledApiKey()).isEqualTo("alpha");
        assertThat(result.getTop3Services())
                .containsExactly(new TopServiceDto("book", 2L), new TopServiceDto("news", 2L));
        assertThat(result.getBrowserRatio().keySet())
                .containsExactly("Chrome", "Firefox");
    }

    private LogEntryDto entry(String apiKey, String serviceId, String browser) {
        return new LogEntryDto(200, "http://apis.kokoa.com/search/" + serviceId + "?apikey=" + apiKey,
                serviceId, apiKey, browser, LocalDateTime.now());
    }
}
