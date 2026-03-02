package com.jw.log_analyzer.service;

import com.jw.log_analyzer.dto.AnalysisResultDto;
import com.jw.log_analyzer.dto.LogEntryDto;
import com.jw.log_analyzer.repository.LogRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogAnalysisServiceTest {

    @Test
    void analyzeAndWriteChoosesLexicographicallySmallestOnCountTie() {
        LogRepository repository = mock(LogRepository.class);
        when(repository.readAllLogs()).thenReturn(List.of(
                entry("beta", "news", "Chrome"),
                entry("beta", "news", "Chrome"),
                entry("alpha", "book", "Firefox"),
                entry("alpha", "book", "Firefox")
        ));

        LogAnalysisService service = new LogAnalysisService(repository);

        AnalysisResultDto result = service.analyzeAndWrite();

        assertThat(result.getMostCalledApiKey()).isEqualTo("alpha");
        assertThat(result.getTop3Services())
                .containsExactly(Map.entry("book", 2L), Map.entry("news", 2L));
        assertThat(result.getBrowserRatio().keySet())
                .containsExactly("Chrome", "Firefox");
    }

    private LogEntryDto entry(String apiKey, String serviceId, String browser) {
        return new LogEntryDto(200, "http://apis.kokoa.com/search/" + serviceId + "?apikey=" + apiKey,
                serviceId, apiKey, browser, LocalDateTime.now());
    }
}
