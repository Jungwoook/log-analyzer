package com.jw.log_analyzer.repository;

import com.jw.log_analyzer.dto.LogEntryDto;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogRepositoryTest {

    @Test
    void readAllLogsIgnoresMalformedApiKeySegmentWithoutQueryDelimiter() {
        LogRepository repository = new LogRepository();
        MockMultipartFile file = multipartFile("logs/kokoa.txt");

        List<LogEntryDto> logs = repository.readAllLogs(file);

        assertThat(logs)
                .filteredOn(log -> log.getUrl().contains("/search/aaaaapikey="))
                .extracting(LogEntryDto::getApiKey)
                .containsOnlyNulls();
    }

    @Test
    void readAllLogsSupportsJsonLineFormat() {
        LogRepository repository = new LogRepository();
        MockMultipartFile file = multipartFile("logs/maver.log");

        List<LogEntryDto> logs = repository.readAllLogs(file);

        assertThat(logs).hasSize(100);
        assertThat(logs.get(0).getServiceId()).isEqualTo("weather");
        assertThat(logs.get(0).getApiKey()).isEqualTo("a1b2c3");
        assertThat(logs.get(0).getBrowser()).isNull();
        assertThat(logs)
                .extracting(LogEntryDto::getServiceId)
                .contains("weather", "stock", "news", "map", "invalid", "beta");
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
