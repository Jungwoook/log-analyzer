package com.jw.log_analyzer.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlFieldExtractor {

    private static final Pattern APIKEY_PATTERN = Pattern.compile("[?&]apikey=([A-Za-z0-9]{4})(?:&|$)");

    public String extractServiceId(String url) {
        String serviceId = extractSegment(url, "/search/");
        if (serviceId != null) {
            return normalizeServiceId(serviceId);
        }
        return extractSegment(url, "/v1/");
    }

    public String extractApiKey(String url) {
        Matcher matcher = APIKEY_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String extractSegment(String url, String marker) {
        int index = url.indexOf(marker);
        if (index < 0) {
            return null;
        }

        int start = index + marker.length();
        int queryIndex = url.indexOf('?', start);
        return sanitizeText(queryIndex >= 0 ? url.substring(start, queryIndex) : url.substring(start));
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

    private String sanitizeText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
