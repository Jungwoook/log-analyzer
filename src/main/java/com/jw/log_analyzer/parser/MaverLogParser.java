package com.jw.log_analyzer.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jw.log_analyzer.dto.LogEntryDto;
import com.jw.log_analyzer.exception.InvalidLogFormatException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@Order(100)
public class MaverLogParser implements LogParser {

    private final ObjectMapper objectMapper;
    private final UrlFieldExtractor urlFieldExtractor;

    public MaverLogParser() {
        this(new ObjectMapper(), new UrlFieldExtractor());
    }

    public MaverLogParser(ObjectMapper objectMapper, UrlFieldExtractor urlFieldExtractor) {
        this.objectMapper = objectMapper;
        this.urlFieldExtractor = urlFieldExtractor;
    }

    @Override
    public boolean supports(String line) {
        return line != null && line.trim().startsWith("{");
    }

    @Override
    public LogEntryDto parse(String line) {
        try {
            JsonNode node = objectMapper.readTree(line.trim());
            int status = node.path("status_code").asInt();
            String url = sanitizeText(node.path("url").asText(null));
            String browser = sanitizeText(node.path("browser").asText(null));
            String serviceId = sanitizeText(node.path("service_id").asText(null));
            String apiKey = sanitizeText(node.path("api_key").asText(null));
            String timestamp = sanitizeText(node.path("@timestamp").asText(null));

            if (timestamp == null || url == null) {
                throw new InvalidLogFormatException("Missing required Maver log fields");
            }

            if (serviceId == null) {
                serviceId = urlFieldExtractor.extractServiceId(url);
            }
            if (apiKey == null) {
                apiKey = urlFieldExtractor.extractApiKey(url);
            }

            LocalDateTime parsedTimestamp = LocalDateTime.ofInstant(Instant.parse(timestamp), ZoneOffset.UTC);
            return new LogEntryDto(status, url, serviceId, apiKey, browser, parsedTimestamp);
        } catch (InvalidLogFormatException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidLogFormatException("Invalid Maver log format: " + e.getMessage(), e);
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
