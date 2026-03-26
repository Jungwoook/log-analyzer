package com.jw.log_analyzer.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jw.log_analyzer.dto.LogEntryDto;
import com.jw.log_analyzer.exception.InvalidLogFormatException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class DefaultLogParser implements LogParser {

    private static final Pattern LINE_PATTERN = Pattern.compile("\\[(\\d+)]\\[(.*?)\\]\\[(.*?)\\]\\[(.*?)\\]");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final UrlFieldExtractor urlFieldExtractor;

    public DefaultLogParser() {
        this(new ObjectMapper(), new UrlFieldExtractor());
    }

    public DefaultLogParser(ObjectMapper objectMapper) {
        this(objectMapper, new UrlFieldExtractor());
    }

    public DefaultLogParser(ObjectMapper objectMapper, UrlFieldExtractor urlFieldExtractor) {
        this.objectMapper = objectMapper;
        this.urlFieldExtractor = urlFieldExtractor;
    }

    @Override
    public boolean supports(String line) {
        return line != null && !line.isBlank();
    }

    @Override
    public LogEntryDto parse(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("{")) {
            return parseJsonLine(trimmed);
        }
        return parseBracketLine(trimmed);
    }

    private LogEntryDto parseBracketLine(String line) {
        java.util.regex.Matcher matcher = LINE_PATTERN.matcher(line);
        if (!matcher.matches()) {
            throw new InvalidLogFormatException("Unsupported bracket log format");
        }

        int status = Integer.parseInt(matcher.group(1));
        String url = matcher.group(2);
        String browser = matcher.group(3);
        String timeStr = matcher.group(4);
        LocalDateTime timestamp = LocalDateTime.parse(timeStr, DATE_FORMAT);

        String serviceId = urlFieldExtractor.extractServiceId(url);
        String apiKey = urlFieldExtractor.extractApiKey(url);
        return new LogEntryDto(status, url, serviceId, apiKey, browser, timestamp);
    }

    private LogEntryDto parseJsonLine(String line) {
        try {
            JsonNode node = objectMapper.readTree(line);
            int status = node.path("status_code").asInt();
            String url = node.path("url").asText(null);
            String browser = sanitizeText(node.path("browser").asText(null));
            String serviceId = sanitizeText(node.path("service_id").asText(null));
            String apiKey = sanitizeText(node.path("api_key").asText(null));
            String timestamp = node.path("@timestamp").asText(null);

            if (timestamp == null || url == null) {
                throw new InvalidLogFormatException("Missing required JSON fields");
            }

            if (serviceId == null) {
                serviceId = urlFieldExtractor.extractServiceId(url);
            }

            LocalDateTime parsedTimestamp = LocalDateTime.ofInstant(Instant.parse(timestamp), ZoneOffset.UTC);
            return new LogEntryDto(status, url, serviceId, apiKey, browser, parsedTimestamp);
        } catch (InvalidLogFormatException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidLogFormatException("Invalid JSON log format: " + e.getMessage(), e);
        }
    }

    private String sanitizeText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
