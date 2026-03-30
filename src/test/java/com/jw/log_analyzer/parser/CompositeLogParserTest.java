package com.jw.log_analyzer.parser;

import com.jw.log_analyzer.dto.LogEntryDto;
import com.jw.log_analyzer.exception.InvalidLogFormatException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositeLogParserTest {

    @Test
    void parseFallsBackToNextParserWhenEarlierParserFails() {
        CompositeLogParser compositeLogParser = new CompositeLogParser(List.of(
                new FailingParser(),
                new SucceedingParser()
        ));

        LogEntryDto result = compositeLogParser.parse("custom-line");

        assertThat(result.getServiceId()).isEqualTo("custom");
        assertThat(result.getApiKey()).isEqualTo("zzzz");
    }

    @Test
    void parseSelectsMaverParserBeforeKokoaParser() {
        CompositeLogParser compositeLogParser = new CompositeLogParser(List.of(
                new MaverLogParser(),
                new KokoaLogParser()
        ));

        LogEntryDto result = compositeLogParser.parse(
                "{\"@timestamp\":\"2012-06-10T08:00:00.000Z\",\"status_code\":200,\"url\":\"http://apis.maver.com/v1/news\",\"service_id\":\"news\",\"api_key\":\"a1b2c3\"}"
        );

        assertThat(result.getUrl()).isEqualTo("http://apis.maver.com/v1/news");
        assertThat(result.getServiceId()).isEqualTo("news");
        assertThat(result.getBrowser()).isNull();
    }

    @Test
    void parsePropagatesFailureWhenNoParserCanHandleLine() {
        CompositeLogParser compositeLogParser = new CompositeLogParser(List.of(
                new MaverLogParser(),
                new KokoaLogParser()
        ));

        assertThatThrownBy(() -> compositeLogParser.parse("totally-invalid"))
                .isInstanceOf(InvalidLogFormatException.class)
                .hasMessageContaining("Unsupported log format");
    }

    private static class FailingParser implements LogParser {
        @Override
        public boolean supports(String line) {
            return true;
        }

        @Override
        public LogEntryDto parse(String line) {
            throw new com.jw.log_analyzer.exception.InvalidLogFormatException("first parser failed");
        }
    }

    private static class SucceedingParser implements LogParser {
        @Override
        public boolean supports(String line) {
            return true;
        }

        @Override
        public LogEntryDto parse(String line) {
            return new LogEntryDto(200, line, "custom", "zzzz", "Chrome", LocalDateTime.now());
        }
    }
}
