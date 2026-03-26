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
    void parseSelectsApacheParserBeforeDefaultCatchAllParser() {
        CompositeLogParser compositeLogParser = new CompositeLogParser(List.of(
                new ApacheAccessLogParser(),
                new DefaultLogParser()
        ));

        LogEntryDto result = compositeLogParser.parse(
                "127.0.0.1 - - [10/Jun/2012:08:00:00 +0000] \"GET /search/news?apikey=a1b2 HTTP/1.1\" 200 2326 \"-\" \"Mozilla/5.0 Chrome/124.0\""
        );

        assertThat(result.getUrl()).isEqualTo("/search/news?apikey=a1b2");
        assertThat(result.getServiceId()).isEqualTo("news");
        assertThat(result.getBrowser()).isEqualTo("Chrome");
    }

    @Test
    void parsePropagatesFailureWhenNoParserCanHandleLine() {
        CompositeLogParser compositeLogParser = new CompositeLogParser(List.of(
                new ApacheAccessLogParser(),
                new DefaultLogParser()
        ));

        assertThatThrownBy(() -> compositeLogParser.parse("totally-invalid"))
                .isInstanceOf(InvalidLogFormatException.class)
                .hasMessageContaining("Unsupported bracket log format");
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
