package com.jw.log_analyzer.parser.runtime;

import com.jw.log_analyzer.parser.contract.LogParser;
import com.jw.log_analyzer.parser.contract.LogRecord;
import com.jw.log_analyzer.parser.contract.ParserContext;
import com.jw.log_analyzer.parser.contract.exception.InvalidLogFormatException;
import com.jw.log_analyzer.parser.implementations.KokoaLogParser;
import com.jw.log_analyzer.parser.implementations.MaverLogParser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositeLogParserTest {

    @Test
    void parseUsesContentSignatureWhenExactlyOneParserMatches() {
        CompositeLogParser compositeLogParser = new CompositeLogParser(List.of(
                new MaverLogParser(),
                new KokoaLogParser()
        ));

        LogRecord result = compositeLogParser.parse(
                "{\"@timestamp\":\"2012-06-10T08:00:00.000Z\",\"status_code\":200,\"url\":\"http://apis.maver.com/v1/news\",\"service_id\":\"news\",\"api_key\":\"a1b2c3\"}",
                "upload.log"
        );

        assertThat(result.getServiceId()).isEqualTo("news");
        assertThat(result.getApiKey()).isEqualTo("a1b2c3");
    }

    @Test
    void parseUsesFileNameHintWhenContentSignatureIsMissing() {
        CompositeLogParser compositeLogParser = new CompositeLogParser(List.of(
                new FileNameOnlyParser("kokoa"),
                new FileNameOnlyParser("maver")
        ));

        LogRecord result = compositeLogParser.parse(
                "unstructured-line",
                "maver-access.log"
        );

        assertThat(result.getServiceId()).isEqualTo("maver");
    }

    @Test
    void parseUsesFileNameToResolveAmbiguousContentSignature() {
        CompositeLogParser compositeLogParser = new CompositeLogParser(List.of(
                new AmbiguousParser("kokoa"),
                new AmbiguousParser("maver")
        ));

        LogRecord result = compositeLogParser.parse("shared-signature-line", "kokoa-20260331.log");

        assertThat(result.getServiceId()).isEqualTo("kokoa");
    }

    @Test
    void parsePropagatesFailureWhenNoParserCanBeSelected() {
        CompositeLogParser compositeLogParser = new CompositeLogParser(List.of(
                new MaverLogParser(),
                new KokoaLogParser()
        ));

        assertThatThrownBy(() -> compositeLogParser.parse("totally-invalid", "access.log"))
                .isInstanceOf(InvalidLogFormatException.class)
                .hasMessageContaining("Unable to determine log source");
    }

    @Test
    void parseFailsWhenContentSignatureIsAmbiguousWithoutHelpfulFileName() {
        CompositeLogParser compositeLogParser = new CompositeLogParser(List.of(
                new AmbiguousParser("kokoa"),
                new AmbiguousParser("maver")
        ));

        assertThatThrownBy(() -> compositeLogParser.parse("shared-signature-line", "access.log"))
                .isInstanceOf(InvalidLogFormatException.class)
                .hasMessageContaining("Ambiguous log signatures");
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
            return new LogRecord(200, context.line(), sourceType, "zzzz", "Chrome", LocalDateTime.now());
        }
    }

    private static class AmbiguousParser implements LogParser {
        private final String sourceType;

        private AmbiguousParser(String sourceType) {
            this.sourceType = sourceType;
        }

        @Override
        public boolean supports(ParserContext context) {
            return "shared-signature-line".equals(context.line());
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
            return new LogRecord(200, context.line(), sourceType, "zzzz", "Chrome", LocalDateTime.now());
        }
    }
}
