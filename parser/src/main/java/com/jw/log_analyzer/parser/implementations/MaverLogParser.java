package com.jw.log_analyzer.parser.implementations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jw.log_analyzer.parser.contract.LogParser;
import com.jw.log_analyzer.parser.contract.LogRecord;
import com.jw.log_analyzer.parser.contract.ParserContext;
import com.jw.log_analyzer.parser.contract.exception.InvalidLogFormatException;
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
    private final ServiceIdPolicy serviceIdPolicy;

    public MaverLogParser() {
        this(new ObjectMapper(), new UrlFieldExtractor(), new MaverServiceIdPolicy());
    }

    public MaverLogParser(ObjectMapper objectMapper, UrlFieldExtractor urlFieldExtractor, ServiceIdPolicy serviceIdPolicy) {
        this.objectMapper = objectMapper;
        this.urlFieldExtractor = urlFieldExtractor;
        this.serviceIdPolicy = serviceIdPolicy;
    }

    @Override
    public boolean supports(ParserContext context) {
        return context.trimmedLine().startsWith("{");
    }

    @Override
    public boolean supportsFileName(String fileName) {
        return fileName != null && fileName.toLowerCase().contains("maver");
    }

    @Override
    public String sourceType() {
        return "maver";
    }

    @Override
    public LogRecord parse(ParserContext context) {
        try {
            JsonNode node = objectMapper.readTree(context.trimmedLine());
            int status = node.path("status_code").asInt();
            String url = sanitizeText(node.path("url").asText(null));
            String browser = sanitizeText(node.path("browser").asText(null));
            String serviceId = serviceIdPolicy.normalize(sanitizeText(node.path("service_id").asText(null)));
            String apiKey = sanitizeText(node.path("api_key").asText(null));
            String timestamp = sanitizeText(node.path("@timestamp").asText(null));

            if (timestamp == null || url == null) {
                throw new InvalidLogFormatException("Missing required Maver log fields");
            }

            if (serviceId == null) {
                serviceId = serviceIdPolicy.normalize(urlFieldExtractor.extractAfterMarker(url, "/v1/"));
            }
            if (apiKey == null) {
                apiKey = urlFieldExtractor.extractApiKey(url);
            }

            if (serviceId == null) {
                throw new InvalidLogFormatException("Missing required Maver serviceId");
            }

            LocalDateTime parsedTimestamp = LocalDateTime.ofInstant(Instant.parse(timestamp), ZoneOffset.UTC);
            return new LogRecord(status, url, serviceId, apiKey, browser, parsedTimestamp);
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
