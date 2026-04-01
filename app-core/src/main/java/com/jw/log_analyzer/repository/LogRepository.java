package com.jw.log_analyzer.repository;

import com.jw.log_analyzer.exception.LogProcessingException;
import com.jw.log_analyzer.parser.contract.LogParser;
import com.jw.log_analyzer.parser.contract.LogRecord;
import com.jw.log_analyzer.parser.contract.exception.InvalidLogFormatException;
import com.jw.log_analyzer.parser.runtime.CompositeLogParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Slf4j
@Repository
public class LogRepository {

    private final CompositeLogParser logParser;

    @Autowired
    public LogRepository(List<LogParser> parsers) {
        this(new CompositeLogParser(parsers));
    }

    public LogRepository(CompositeLogParser logParser) {
        this.logParser = logParser;
    }

    public Stream<LogRecord> streamLogs(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        final BufferedReader reader;
        try {
            InputStream inputStream = file.getInputStream();
            reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read logs", e);
        }
        AtomicLong lineNumber = new AtomicLong(0);

        return reader.lines()
                .filter(this::hasText)
                .map(line -> safeParseLine(line, fileName, lineNumber.incrementAndGet()))
                .onClose(() -> closeQuietly(reader));
    }

    private LogRecord parseLine(String line, String fileName) {
        return logParser.parse(line, fileName);
    }

    private LogRecord safeParseLine(String line, String fileName, long lineNumber) {
        try {
            return parseLine(line, fileName);
        } catch (InvalidLogFormatException e) {
            log.warn("Invalid log format detected. lineNumber={}, error={}", lineNumber, e.getMessage());
            throw new InvalidLogFormatException("Unsupported log format at line " + lineNumber + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("Unexpected log processing error. lineNumber={}", lineNumber, e);
            throw new LogProcessingException("Unexpected error while processing log at line " + lineNumber, e);
        }
    }

    private void closeQuietly(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException e) {
            log.warn("Failed to close log reader", e);
        }
    }

    private boolean hasText(String line) {
        return line != null && !line.isBlank();
    }

}
