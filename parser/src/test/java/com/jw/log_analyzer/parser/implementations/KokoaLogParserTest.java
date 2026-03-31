package com.jw.log_analyzer.parser.implementations;

import com.jw.log_analyzer.parser.contract.LogRecord;
import com.jw.log_analyzer.parser.contract.ParserContext;
import com.jw.log_analyzer.parser.contract.exception.InvalidLogFormatException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KokoaLogParserTest {

    private final KokoaLogParser parser = new KokoaLogParser();

    @Test
    void supportsKokoaBracketFormatOnly() {
        assertThat(parser.supports(new ParserContext("[200][http://apis.kokoa.com/search/news?apikey=a1b2][Chrome][2012-06-10 08:00:00]", "kokoa.log"))).isTrue();
        assertThat(parser.supports(new ParserContext("{\"@timestamp\":\"2012-06-10T08:00:00.000Z\",\"status_code\":200,\"url\":\"http://apis.maver.com/v1/news\",\"service_id\":\"news\",\"api_key\":\"a1b2c3\"}", "maver.log"))).isFalse();
        assertThat(parser.supports(new ParserContext("   ", "blank.log"))).isFalse();
    }

    @Test
    void parseBracketLineProducesCommonLogModel() {
        LogRecord result = parser.parse(new ParserContext("[200][http://apis.kokoa.com/search/news?apikey=a1b2][Chrome][2012-06-10 08:00:00]", "kokoa.log"));

        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(result.getServiceId()).isEqualTo("news");
        assertThat(result.getApiKey()).isEqualTo("a1b2");
        assertThat(result.getBrowser()).isEqualTo("Chrome");
    }

    @Test
    void parseInvalidLineThrowsException() {
        assertThatThrownBy(() -> parser.parse(new ParserContext("invalid-line", "kokoa.log")))
                .isInstanceOf(InvalidLogFormatException.class)
                .hasMessageContaining("Unsupported kokoa log format");
    }

    @Test
    void parseRejectsUnsupportedKokoaServiceId() {
        assertThatThrownBy(() -> parser.parse(new ParserContext(
                "[200][http://apis.kokoa.com/search/unknown?apikey=a1b2][Chrome][2012-06-10 08:00:00]",
                "kokoa.log"
        )))
                .isInstanceOf(InvalidLogFormatException.class)
                .hasMessageContaining("Unsupported Kokoa serviceId");
    }
}
