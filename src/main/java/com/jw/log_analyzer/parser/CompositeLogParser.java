package com.jw.log_analyzer.parser;

import com.jw.log_analyzer.dto.LogEntryDto;
import com.jw.log_analyzer.exception.InvalidLogFormatException;

import java.util.ArrayList;
import java.util.List;

public class CompositeLogParser {

    private final List<LogParser> parsers;

    public CompositeLogParser(List<LogParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public LogEntryDto parse(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        List<String> errors = new ArrayList<>();
        for (LogParser parser : parsers) {
            if (!parser.supports(trimmed)) {
                continue;
            }
            try {
                return parser.parse(trimmed);
            } catch (InvalidLogFormatException e) {
                errors.add(e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            throw new InvalidLogFormatException(String.join(" | ", errors));
        }
        throw new InvalidLogFormatException("Unsupported log format");
    }
}
