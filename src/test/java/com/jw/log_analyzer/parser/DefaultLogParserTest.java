package com.jw.log_analyzer.parser;

import com.jw.log_analyzer.dto.LogEntryDto;
import com.jw.log_analyzer.exception.InvalidLogFormatException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultLogParserTest {

    private final DefaultLogParser parser = new DefaultLogParser();

    @Test
    void supportsBracketAndJsonFormats() {
        assertThat(parser.supports("[200][http://apis.kokoa.com/search/news?apikey=a1b2][Chrome][2012-06-10 08:00:00]")).isTrue();
        assertThat(parser.supports("{\"@timestamp\":\"2012-06-10T08:00:00.000Z\",\"status_code\":200,\"url\":\"http://apis.maver.com/v1/news\",\"service_id\":\"news\",\"api_key\":\"a1b2c3\"}")).isTrue();
        assertThat(parser.supports("   ")).isFalse();
    }

    @Test
    void parseBracketLineProducesCommonLogModel() {
        LogEntryDto result = parser.parse("[200][http://apis.kokoa.com/search/news?apikey=a1b2][Chrome][2012-06-10 08:00:00]");

        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(result.getServiceId()).isEqualTo("news");
        assertThat(result.getApiKey()).isEqualTo("a1b2");
        assertThat(result.getBrowser()).isEqualTo("Chrome");
    }

    @Test
    void parseJsonLineProducesCommonLogModel() {
        LogEntryDto result = parser.parse("{\"@timestamp\":\"2012-06-10T08:00:00.000Z\",\"status_code\":200,\"url\":\"http://apis.maver.com/v1/news\",\"service_id\":\"news\",\"api_key\":\"a1b2c3\"}");

        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(result.getServiceId()).isEqualTo("news");
        assertThat(result.getApiKey()).isEqualTo("a1b2c3");
        assertThat(result.getBrowser()).isNull();
    }

    @Test
    void parseInvalidLineThrowsException() {
        assertThatThrownBy(() -> parser.parse("invalid-line"))
                .isInstanceOf(InvalidLogFormatException.class)
                .hasMessageContaining("Unsupported bracket log format");
    }
}
