package com.jw.log_analyzer.dto;

import lombok.Value;

import java.time.LocalDateTime;

@Value
public class LogEntryDto {
    int statusCode;
    String url;
    String serviceId;
    String apiKey;
    String browser;
    LocalDateTime timestamp;
}
