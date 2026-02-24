package com.jw.log_analyzer.dto;

import java.time.LocalDateTime;

public class LogEntryDto {
    private final int statusCode;
    private final String url;
    private final String apiService;
    private final String apiKey;
    private final String browser;
    private final LocalDateTime timestamp;

    public LogEntryDto(int statusCode, String url, String apiService, String apiKey, String browser, LocalDateTime timestamp) {
        this.statusCode = statusCode;
        this.url = url;
        this.apiService = apiService;
        this.apiKey = apiKey;
        this.browser = browser;
        this.timestamp = timestamp;
    }

    public int getStatusCode() { return statusCode; }
    public String getUrl() { return url; }
    public String getApiService() { return apiService; }
    public String getApiKey() { return apiKey; }
    public String getBrowser() { return browser; }
    public java.time.LocalDateTime getTimestamp() { return timestamp; }
}