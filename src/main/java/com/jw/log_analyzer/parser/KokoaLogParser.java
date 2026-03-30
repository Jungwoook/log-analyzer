package com.jw.log_analyzer.parser;

import com.jw.log_analyzer.dto.LogEntryDto;
import com.jw.log_analyzer.exception.InvalidLogFormatException;
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

    public KokoaLogParser() {
        this(new UrlFieldExtractor());
    }

    public KokoaLogParser(UrlFieldExtractor urlFieldExtractor) {
        this.urlFieldExtractor = urlFieldExtractor;
    }

    @Override
    public boolean supports(String line) {
        return line != null && KOKOA_LOG_PATTERN.matcher(line.trim()).matches();
    }

    @Override
    public LogEntryDto parse(String line) {
        java.util.regex.Matcher matcher = KOKOA_LOG_PATTERN.matcher(line.trim());
        if (!matcher.matches()) {
            throw new InvalidLogFormatException("Unsupported kokoa log format");
        }

        int status = Integer.parseInt(matcher.group(1));
        String url = matcher.group(2);
        String browser = matcher.group(3);
        String timeStr = matcher.group(4);
        LocalDateTime timestamp = LocalDateTime.parse(timeStr, DATE_FORMAT);

        return new LogEntryDto(
                status,
                url,
                urlFieldExtractor.extractServiceId(url),
                urlFieldExtractor.extractApiKey(url),
                browser,
                timestamp
        );
    }
}
