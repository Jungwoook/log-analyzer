package com.jw.log_analyzer.repository;

import com.jw.log_analyzer.parser.contract.LogParser;
import com.jw.log_analyzer.parser.contract.LogRecord;
import com.jw.log_analyzer.parser.contract.ParserContext;
import com.jw.log_analyzer.parser.implementations.KokoaLogParser;
import com.jw.log_analyzer.parser.implementations.MaverLogParser;
import com.jw.log_analyzer.parser.runtime.CompositeLogParser;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogRepositoryTest {

    @Test
    void streamLogsIgnoresMalformedApiKeySegmentWithoutQueryDelimiter() {
        LogRepository repository = new LogRepository(new CompositeLogParser(List.of(new MaverLogParser(), new KokoaLogParser())));
        MockMultipartFile file = multipartFile("logs/kokoa.txt");

        List<LogRecord> logs = readAll(repository, file);

        assertThat(logs)
                .filteredOn(log -> log.getUrl().contains("/search/aaaaapikey="))
                .extracting(LogRecord::getApiKey)
                .containsOnlyNulls();
    }

    @Test
    void streamLogsSupportsJsonLineFormat() {
        LogRepository repository = new LogRepository(new CompositeLogParser(List.of(new MaverLogParser(), new KokoaLogParser())));
        MockMultipartFile file = multipartFile("logs/maver.log");

        List<LogRecord> logs = readAll(repository, file);

        assertThat(logs).hasSize(100);
        assertThat(logs.get(0).getServiceId()).isEqualTo("weather");
        assertThat(logs.get(0).getApiKey()).isEqualTo("a1b2c3");
        assertThat(logs.get(0).getBrowser()).isNull();
        assertThat(logs)
                .extracting(LogRecord::getServiceId)
                .contains("weather", "stock", "news", "map", "invalid", "beta");
    }

    @Test
    void streamLogsUsesFileNameHintWhenContentHasNoSignature() {
        LogRepository repository = new LogRepository(new CompositeLogParser(List.of(new FileNameOnlyParser("maver"))));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "maver-access.log",
                "text/plain",
                "plain-line".getBytes(StandardCharsets.UTF_8)
        );

        List<LogRecord> logs = readAll(repository, file);

        assertThat(logs).singleElement()
                .extracting(LogRecord::getServiceId)
                .isEqualTo("maver");
    }

    @Test
    void streamLogsFailsWhenSourceCannotBeDetermined() {
        LogRepository repository = new LogRepository(new CompositeLogParser(List.of(new FileNameOnlyParser("maver"))));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "access.log",
                "text/plain",
                "plain-line".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> readAll(repository, file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unable to determine log source");
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

    private List<LogRecord> readAll(LogRepository repository, MockMultipartFile file) {
        try (var logs = repository.streamLogs(file)) {
            return logs.toList();
        }
    }

    private static class FileNameOnlyParser implements LogParser {
        private final String sourceType;

        private FileNameOnlyParser(String sourceType) {
            this.sourceType = sourceType;
        }

        @Override
        public boolean supports(ParserContext context) {
            return false;
        }

        @Override
        public boolean supportsFileName(String fileName) {
            return fileName != null && fileName.contains(sourceType);
        }

        @Override
        public String sourceType() {
            return sourceType;
        }

        @Override
        public LogRecord parse(ParserContext context) {
            return new LogRecord(200, context.line(), sourceType, null, null, null);
        }
    }
}
