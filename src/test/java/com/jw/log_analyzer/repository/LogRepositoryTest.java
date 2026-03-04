package com.jw.log_analyzer.repository;

import com.jw.log_analyzer.dto.LogEntryDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogRepositoryTest {

    @Test
    void readAllLogsIgnoresMalformedApiKeySegmentWithoutQueryDelimiter() {
        LogRepository repository = new LogRepository();

        List<LogEntryDto> logs = repository.readAllLogs();

        assertThat(logs)
                .filteredOn(log -> log.getUrl().contains("/search/aaaaapikey="))
                .extracting(LogEntryDto::getApiKey)
                .containsOnlyNulls();
    }

    @Test
    void readAllLogsSupportsJsonLineFormat() {
        LogRepository repository = new LogRepository();

        List<LogEntryDto> logs = repository.readAllLogs("logs/maver.log");

        assertThat(logs).hasSize(6);
        assertThat(logs.get(0).getServiceId()).isEqualTo("news");
        assertThat(logs.get(0).getApiKey()).isEqualTo("a1b2");
        assertThat(logs.get(0).getBrowser()).isEqualTo("Chrome");
        assertThat(logs.get(4).getServiceId()).isNull();
    }
}
