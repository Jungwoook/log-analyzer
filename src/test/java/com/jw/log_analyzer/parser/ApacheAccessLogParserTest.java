package com.jw.log_analyzer.parser;

import com.jw.log_analyzer.dto.LogEntryDto;
import com.jw.log_analyzer.exception.InvalidLogFormatException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApacheAccessLogParserTest {

    private final ApacheAccessLogParser parser = new ApacheAccessLogParser();

    @Test
    void supportsApacheAccessLogFormatOnly() {
        assertThat(parser.supports("127.0.0.1 - - [10/Jun/2012:08:00:00 +0000] \"GET /search/news?apikey=a1b2 HTTP/1.1\" 200 2326 \"-\" \"Mozilla/5.0 Chrome/124.0\"")).isTrue();
        assertThat(parser.supports("[200][http://apis.kokoa.com/search/news?apikey=a1b2][Chrome][2012-06-10 08:00:00]")).isFalse();
    }

    @Test
    void parseApacheAccessLogProducesCommonLogModel() {
        LogEntryDto result = parser.parse("127.0.0.1 - - [10/Jun/2012:08:00:00 +0000] \"GET /search/news?apikey=a1b2 HTTP/1.1\" 200 2326 \"-\" \"Mozilla/5.0 Chrome/124.0\"");

        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(result.getUrl()).isEqualTo("/search/news?apikey=a1b2");
        assertThat(result.getServiceId()).isEqualTo("news");
        assertThat(result.getApiKey()).isEqualTo("a1b2");
        assertThat(result.getBrowser()).isEqualTo("Chrome");
    }

    @Test
    void parseInvalidApacheLineThrowsException() {
        assertThatThrownBy(() -> parser.parse("127.0.0.1 - - malformed"))
                .isInstanceOf(InvalidLogFormatException.class)
                .hasMessageContaining("Unsupported apache access log format");
    }
}
