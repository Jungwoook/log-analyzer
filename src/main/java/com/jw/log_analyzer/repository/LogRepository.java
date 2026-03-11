package com.jw.log_analyzer.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jw.log_analyzer.dto.LogEntryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
public class LogRepository {

    private static final Logger log = LoggerFactory.getLogger(LogRepository.class);
    private static final Pattern LINE_PATTERN = Pattern.compile("\\[(\\d+)]\\[(.*?)\\]\\[(.*?)\\]\\[(.*?)\\]");
    private static final Pattern APIKEY_PATTERN = Pattern.compile("[?&]apikey=([A-Za-z0-9]{4})(?:&|$)");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<LogEntryDto> readAllLogs(MultipartFile file) {
        try (Stream<LogEntryDto> logs = streamLogs(file)) {
            return logs.collect(Collectors.toCollection(ArrayList::new));
        }
    }

    public Stream<LogEntryDto> streamLogs(MultipartFile file) {
        final BufferedReader reader;
        try {
            InputStream inputStream = file.getInputStream();
            reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read logs", e);
        }
        AtomicLong lineNumber = new AtomicLong(0);

        return reader.lines()
                .map(line -> safeParseLine(line, lineNumber.incrementAndGet()))
                .filter(Objects::nonNull)
                .onClose(() -> closeQuietly(reader));
    }

    private LogEntryDto parseLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.startsWith("{")) {
            return parseJsonLine(trimmed);
        }
        return parseBracketLine(trimmed);
    }

    private LogEntryDto safeParseLine(String line, long lineNumber) {
        try {
            return parseLine(line);
        } catch (RuntimeException e) {
            log.warn("Failed to parse log line. lineNumber={}, error={}", lineNumber, e.getMessage());
            return null;
        }
    }

    private void closeQuietly(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to close log reader", e);
        }
    }

    private LogEntryDto parseBracketLine(String line) {
        Matcher m = LINE_PATTERN.matcher(line);
        if (!m.find()) {
            throw new IllegalArgumentException("Unsupported bracket log format");
        }

        int status = Integer.parseInt(m.group(1));
        String url = m.group(2);
        String browser = m.group(3);
        String timeStr = m.group(4);
        LocalDateTime ts = LocalDateTime.parse(timeStr, DATE_FORMAT);
        
        String serviceId = extractServiceIdFromUrl(url);
        String apiKey = extractApiKeyFromUrl(url);
        return new LogEntryDto(status, url, serviceId, apiKey, browser, ts);
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
                throw new IllegalArgumentException("Missing required JSON fields");
            }

            if (serviceId == null) {
                serviceId = extractServiceIdFromUrl(url);
            }

            LocalDateTime ts = LocalDateTime.ofInstant(Instant.parse(timestamp), ZoneOffset.UTC);
            return new LogEntryDto(status, url, serviceId, apiKey, browser, ts);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON log format: " + e.getMessage(), e);
        }
    }

    private String extractServiceIdFromUrl(String url) {
        String serviceId = extractSegment(url, "/search/");
        if (serviceId != null) {
            return normalizeServiceId(serviceId);
        }
        return extractSegment(url, "/v1/");
    }

    private String extractSegment(String url, String marker) {
        int idx = url.indexOf(marker);
        if (idx < 0) {
            return null;
        }

        int start = idx + marker.length();
        int q = url.indexOf('?', start);
        return sanitizeText(q >= 0 ? url.substring(start, q) : url.substring(start));
    }

    private String normalizeServiceId(String serviceId) {
        if (serviceId == null) {
            return null;
        }
        switch (serviceId) {
            case "blog":
            case "book":
            case "image":
            case "knowledge":
            case "news":
            case "vclip":
                return serviceId;
            default:
                return null;
        }
    }

    private String extractApiKeyFromUrl(String url) {
        Matcher m = APIKEY_PATTERN.matcher(url);
        if (m.find()) return m.group(1);
        return null;
    }

    private String sanitizeText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
