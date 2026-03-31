package com.jw.log_analyzer.parser.implementations;

import com.jw.log_analyzer.parser.contract.LogRecord;
import com.jw.log_analyzer.parser.contract.ParserContext;
import com.jw.log_analyzer.parser.contract.exception.InvalidLogFormatException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaverLogParserTest {

    private final MaverLogParser parser = new MaverLogParser();

    @Test
    void supportsMaverLogFormatOnly() {
        assertThat(parser.supports(new ParserContext("{\"@timestamp\":\"2012-06-10T08:00:00.000Z\",\"status_code\":200,\"url\":\"http://apis.maver.com/v1/news\",\"service_id\":\"news\",\"api_key\":\"a1b2c3\"}", "maver.log"))).isTrue();
        assertThat(parser.supports(new ParserContext("[200][http://apis.kokoa.com/search/news?apikey=a1b2][Chrome][2012-06-10 08:00:00]", "kokoa.log"))).isFalse();
    }

    @Test
    void parseMaverLogProducesCommonLogModel() {
        LogRecord result = parser.parse(new ParserContext("{\"@timestamp\":\"2012-06-10T08:00:00.000Z\",\"status_code\":200,\"url\":\"http://apis.maver.com/v1/news\",\"service_id\":\"news\",\"api_key\":\"a1b2c3\"}", "maver.log"));

        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(result.getUrl()).isEqualTo("http://apis.maver.com/v1/news");
        assertThat(result.getServiceId()).isEqualTo("news");
        assertThat(result.getApiKey()).isEqualTo("a1b2c3");
        assertThat(result.getBrowser()).isNull();
    }

    @Test
    void parseInvalidMaverLineThrowsException() {
        assertThatThrownBy(() -> parser.parse(new ParserContext("{\"status_code\":200}", "maver.log")))
                .isInstanceOf(InvalidLogFormatException.class)
                .hasMessageContaining("Missing required Maver log fields");
    }

    @Test
    void parseAllowsPreviouslyUnknownMaverServiceId() {
        LogRecord result = parser.parse(new ParserContext(
                "{\"@timestamp\":\"2012-06-10T08:00:00.000Z\",\"status_code\":200,\"url\":\"http://apis.maver.com/v1/beta\",\"service_id\":\"beta\",\"api_key\":\"a1b2c3\"}",
                "maver.log"
        ));

        assertThat(result.getServiceId()).isEqualTo("beta");
    }
}
