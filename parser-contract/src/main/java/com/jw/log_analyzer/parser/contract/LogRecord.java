package com.jw.log_analyzer.parser.contract;

import lombok.Value;

import java.time.LocalDateTime;

@Value
public class LogRecord {
    int statusCode;
    String url;
    String serviceId;
    String apiKey;
    String browser;
    LocalDateTime timestamp;
}
