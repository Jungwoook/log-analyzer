package com.jw.log_analyzer.repository;

import com.jw.log_analyzer.dto.LogEntryDto;
import com.jw.log_analyzer.exception.InvalidLogFormatException;
import com.jw.log_analyzer.parser.ApacheAccessLogParser;
import com.jw.log_analyzer.parser.CompositeLogParser;
import com.jw.log_analyzer.parser.DefaultLogParser;
import com.jw.log_analyzer.parser.LogParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Repository
public class LogRepository {

    private static final Logger log = LoggerFactory.getLogger(LogRepository.class);
    private final CompositeLogParser logParser;

    public LogRepository() {
        this(new CompositeLogParser(List.of(
                new ApacheAccessLogParser(),
                new DefaultLogParser()
        )));
    }

    @Autowired
    public LogRepository(List<LogParser> parsers) {
        this(new CompositeLogParser(parsers));
    }

    LogRepository(CompositeLogParser logParser) {
        this.logParser = logParser;
    }

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
        return logParser.parse(line);
    }

    private LogEntryDto safeParseLine(String line, long lineNumber) {
        try {
            return parseLine(line);
        } catch (InvalidLogFormatException e) {
            log.warn("Invalid log format detected. lineNumber={}, error={}", lineNumber, e.getMessage());
            throw new InvalidLogFormatException("Unsupported log format at line " + lineNumber + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.warn("Invalid log format detected. lineNumber={}, error={}", lineNumber, e.getMessage());
            throw new InvalidLogFormatException("Unsupported log format at line " + lineNumber, e);
        }
    }

    private void closeQuietly(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to close log reader", e);
        }
    }

}
