package com.jw.log_analyzer.parser.runtime;

import com.jw.log_analyzer.parser.contract.LogParser;
import com.jw.log_analyzer.parser.contract.ParserContext;
import com.jw.log_analyzer.parser.contract.exception.InvalidLogFormatException;

import java.util.List;
import java.util.stream.Collectors;

public class ParserSelectionPolicy {

    public LogParser select(List<LogParser> parsers, ParserContext context) {
        List<LogParser> contentMatches = parsers.stream()
                .filter(parser -> parser.supports(context))
                .toList();

        if (contentMatches.size() == 1) {
            return contentMatches.get(0);
        }

        if (contentMatches.size() > 1) {
            return resolveByFileName(contentMatches, context.fileName(), "Ambiguous log signatures");
        }

        List<LogParser> fileNameMatches = parsers.stream()
                .filter(parser -> parser.supportsFileName(context.fileName()))
                .toList();

        if (fileNameMatches.size() == 1) {
            return fileNameMatches.get(0);
        }

        if (fileNameMatches.size() > 1) {
            throw new InvalidLogFormatException("Ambiguous file name hints: " + describe(fileNameMatches));
        }

        throw new InvalidLogFormatException("Unable to determine log source from content or file name");
    }

    private LogParser resolveByFileName(List<LogParser> candidates, String fileName, String messagePrefix) {
        List<LogParser> fileNameMatches = candidates.stream()
                .filter(parser -> parser.supportsFileName(fileName))
                .toList();

        if (fileNameMatches.size() == 1) {
            return fileNameMatches.get(0);
        }

        if (fileNameMatches.isEmpty()) {
            throw new InvalidLogFormatException(messagePrefix + ": " + describe(candidates));
        }

        throw new InvalidLogFormatException("Ambiguous file name hints: " + describe(fileNameMatches));
    }

    private String describe(List<LogParser> parsers) {
        return parsers.stream()
                .map(LogParser::sourceType)
                .collect(Collectors.joining(", "));
    }
}
