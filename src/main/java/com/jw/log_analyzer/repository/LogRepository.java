package com.jw.log_analyzer.repository;

import com.jw.log_analyzer.dto.LogEntryDto;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Repository
public class LogRepository {

    private static final Pattern LINE_PATTERN = Pattern.compile("\\[(\\d+)]\\[(.*?)\\]\\[(.*?)\\]\\[(.*?)\\]");
    private static final Pattern APIKEY_PATTERN = Pattern.compile("apikey=([A-Za-z0-9]{4})");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<LogEntryDto> readAllLogs() {
        List<LogEntryDto> result = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new ClassPathResource("logs/kokoa.txt").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = LINE_PATTERN.matcher(line);
                if (!m.find()) continue;
                int status = Integer.parseInt(m.group(1));
                String url = m.group(2);
                String browser = m.group(3);
                String timeStr = m.group(4);
                LocalDateTime ts = LocalDateTime.parse(timeStr, DATE_FORMAT);

                String apiService = extractServiceId(url);
                String apiKey = extractApiKey(url);

                result.add(new LogEntryDto(status, url, apiService, apiKey, browser, ts));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read logs", e);
        }
        return result;
    }

    private String extractServiceId(String url) {
        // get segment after /search/ until '?' or end
        int idx = url.indexOf("/search/");
        if (idx < 0) return null;
        int start = idx + "/search/".length();
        int q = url.indexOf('?', start);
        String seg = q >= 0 ? url.substring(start, q) : url.substring(start);
        // only accept known services
        switch (seg) {
            case "blog":
            case "book":
            case "image":
            case "knowledge":
            case "news":
            case "vclip":
                return seg;
            default:
                return null;
        }
    }

    private String extractApiKey(String url) {
        Matcher m = APIKEY_PATTERN.matcher(url);
        if (m.find()) return m.group(1);
        return null;
    }
}