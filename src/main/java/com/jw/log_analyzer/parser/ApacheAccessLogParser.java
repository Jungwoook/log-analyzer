package com.jw.log_analyzer.parser;

import com.jw.log_analyzer.dto.LogEntryDto;
import com.jw.log_analyzer.exception.InvalidLogFormatException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(100)
public class ApacheAccessLogParser implements LogParser {

    private static final Pattern APACHE_LOG_PATTERN = Pattern.compile(
            "^(\\S+) \\S+ \\S+ \\[([^]]+)] \"(\\S+) ([^\"]+) (HTTP/[^\"]+)\" (\\d{3}) \\S+ \"[^\"]*\" \"([^\"]*)\"$"
    );
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);

    private final UrlFieldExtractor urlFieldExtractor;

    public ApacheAccessLogParser() {
        this(new UrlFieldExtractor());
    }

    public ApacheAccessLogParser(UrlFieldExtractor urlFieldExtractor) {
        this.urlFieldExtractor = urlFieldExtractor;
    }

    @Override
    public boolean supports(String line) {
        return line != null && APACHE_LOG_PATTERN.matcher(line.trim()).matches();
    }

    @Override
    public LogEntryDto parse(String line) {
        Matcher matcher = APACHE_LOG_PATTERN.matcher(line.trim());
        if (!matcher.matches()) {
            throw new InvalidLogFormatException("Unsupported apache access log format");
        }

        String requestTarget = matcher.group(4);
        int statusCode = Integer.parseInt(matcher.group(6));
        String userAgent = matcher.group(7);
        LocalDateTime timestamp = OffsetDateTime.parse(matcher.group(2), DATE_FORMAT).toLocalDateTime();

        return new LogEntryDto(
                statusCode,
                requestTarget,
                urlFieldExtractor.extractServiceId(requestTarget),
                urlFieldExtractor.extractApiKey(requestTarget),
                extractBrowser(userAgent),
                timestamp
        );
    }

    private String extractBrowser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        if (userAgent.contains("Edg")) {
            return "Edge";
        }
        if (userAgent.contains("Chrome")) {
            return "Chrome";
        }
        if (userAgent.contains("Firefox")) {
            return "Firefox";
        }
        if (userAgent.contains("Safari")) {
            return "Safari";
        }
        return null;
    }
}
