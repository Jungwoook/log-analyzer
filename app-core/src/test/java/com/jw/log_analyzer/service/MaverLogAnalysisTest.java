package com.jw.log_analyzer.service;

import com.jw.log_analyzer.analysis.BrowserRatioCalculator;
import com.jw.log_analyzer.analysis.LogAnalysisResultAssembler;
import com.jw.log_analyzer.analysis.MostCalledApiKeyCalculator;
import com.jw.log_analyzer.analysis.TopServicesCalculator;
import com.jw.log_analyzer.dto.AnalysisResultDto;
import com.jw.log_analyzer.dto.AnalysisResultDto.TopServiceDto;
import com.jw.log_analyzer.parser.implementations.KokoaLogParser;
import com.jw.log_analyzer.parser.implementations.MaverLogParser;
import com.jw.log_analyzer.parser.runtime.CompositeLogParser;
import com.jw.log_analyzer.repository.LogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

class MaverLogAnalysisTest {

    @Test
    void maverJsonLogProducesExpectedStatistics() {
        LogAnalysisResultAssembler resultAssembler = new LogAnalysisResultAssembler(
                new MostCalledApiKeyCalculator(),
                new TopServicesCalculator(),
                new BrowserRatioCalculator()
        );
        LogAnalysisService service = new LogAnalysisService(
                new LogRepository(new CompositeLogParser(java.util.List.of(new MaverLogParser(), new KokoaLogParser()))),
                resultAssembler,
                new AnalysisResultDtoMapper()
        );
        MockMultipartFile file = multipartFile("logs/maver.log");

        AnalysisResultDto result = service.analyze(file);

        assertThat(result.getMostCalledApiKey()).isEqualTo("a1b2c3");
        assertThat(result.getTop3Services())
                .containsExactly(
                        new TopServiceDto("weather", 24L),
                        new TopServiceDto("news", 23L),
                        new TopServiceDto("stock", 23L)
                );
        assertThat(result.getBrowserRatio()).isEmpty();
    }

    private MockMultipartFile multipartFile(String classpathLocation) {
        try {
            ClassPathResource resource = new ClassPathResource(classpathLocation);
            return new MockMultipartFile(
                    "file",
                    resource.getFilename(),
                    "text/plain",
                    resource.getInputStream()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
