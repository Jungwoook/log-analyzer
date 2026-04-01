package com.jw.log_analyzer.parser.implementations;

import com.jw.log_analyzer.parser.contract.LogParser;
import com.jw.log_analyzer.parser.contract.LogRecord;
import com.jw.log_analyzer.parser.contract.ParserContext;
import com.jw.log_analyzer.parser.contract.exception.InvalidLogFormatException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class KokoaLogParser implements LogParser {

    private static final Pattern KOKOA_LOG_PATTERN = Pattern.compile("\\[(\\d+)]\\[(.*?)\\]\\[(.*?)\\]\\[(.*?)\\]");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UrlFieldExtractor urlFieldExtractor;
    private final ServiceIdPolicy serviceIdPolicy;

    public KokoaLogParser() {
        this(new UrlFieldExtractor(), new KokoaServiceIdPolicy());
    }

    public KokoaLogParser(UrlFieldExtractor urlFieldExtractor, ServiceIdPolicy serviceIdPolicy) {
        this.urlFieldExtractor = urlFieldExtractor;
        this.serviceIdPolicy = serviceIdPolicy;
    }

    @Override
    public boolean supports(ParserContext context) {
        return KOKOA_LOG_PATTERN.matcher(context.trimmedLine()).matches();
    }

    @Override
    public boolean supportsFileName(String fileName) {
        return fileName != null && fileName.toLowerCase().contains("kokoa");
    }

    @Override
    public String sourceType() {
        return "kokoa";
    }

    @Override
    public LogRecord parse(ParserContext context) {
        java.util.regex.Matcher matcher = KOKOA_LOG_PATTERN.matcher(context.trimmedLine());
        if (!matcher.matches()) {
            throw new InvalidLogFormatException("Unsupported kokoa log format");
        }

        int status = Integer.parseInt(matcher.group(1));
        String url = matcher.group(2);
        String browser = matcher.group(3);
        String timeStr = matcher.group(4);
        LocalDateTime timestamp = LocalDateTime.parse(timeStr, DATE_FORMAT);
        String rawServiceId = urlFieldExtractor.extractAfterMarker(url, "/search/");
        String serviceId = normalizeServiceId(rawServiceId);

        if (serviceId == null && !isRecoverableMalformedSegment(rawServiceId)) {
            throw new InvalidLogFormatException("Unsupported Kokoa serviceId");
        }

        return new LogRecord(
                status,
                url,
                serviceId,
                urlFieldExtractor.extractApiKey(url),
                browser,
                timestamp
        );
    }

    private String normalizeServiceId(String rawServiceId) {
        return serviceIdPolicy.normalize(rawServiceId);
    }

    private boolean isRecoverableMalformedSegment(String rawServiceId) {
        return rawServiceId != null && rawServiceId.contains("apikey=");
    }
}
