package com.jw.log_analyzer.service;

import com.jw.log_analyzer.analysis.BrowserRatioCalculator;
import com.jw.log_analyzer.analysis.LogAnalysisResultAssembler;
import com.jw.log_analyzer.analysis.MostCalledApiKeyCalculator;
import com.jw.log_analyzer.analysis.TopServicesCalculator;
import com.jw.log_analyzer.dto.AnalysisResultDto;
import com.jw.log_analyzer.dto.AnalysisResultDto.TopServiceDto;
import com.jw.log_analyzer.dto.LogEntryDto;
import com.jw.log_analyzer.repository.LogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogAnalysisServiceTest {

    @Test
    void analyzeChoosesLexicographicallySmallestOnCountTie() {
        LogRepository repository = mock(LogRepository.class);
        MockMultipartFile file = new MockMultipartFile("file", "upload.log", "text/plain", new byte[0]);
        when(repository.streamLogs(any())).thenReturn(Stream.of(
                entry("beta", "news", "Chrome"),
                entry("beta", "news", "Chrome"),
                entry("alpha", "book", "Firefox"),
                entry("alpha", "book", "Firefox")
        ));

        LogAnalysisResultAssembler resultAssembler = new LogAnalysisResultAssembler(
                new MostCalledApiKeyCalculator(),
                new TopServicesCalculator(),
                new BrowserRatioCalculator()
        );
        LogAnalysisService service = new LogAnalysisService(repository, resultAssembler);

        AnalysisResultDto result = service.analyze(file);

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
