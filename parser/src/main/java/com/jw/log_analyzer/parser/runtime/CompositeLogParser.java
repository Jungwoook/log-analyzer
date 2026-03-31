package com.jw.log_analyzer.parser.runtime;

import com.jw.log_analyzer.parser.contract.LogParser;
import com.jw.log_analyzer.parser.contract.LogRecord;
import com.jw.log_analyzer.parser.contract.ParserContext;
import java.util.List;

public class CompositeLogParser {

    private final List<LogParser> parsers;
    private final ParserSelectionPolicy selectionPolicy;

    public CompositeLogParser(List<LogParser> parsers) {
        this(parsers, new ParserSelectionPolicy());
    }

    public CompositeLogParser(List<LogParser> parsers, ParserSelectionPolicy selectionPolicy) {
        this.parsers = List.copyOf(parsers);
        this.selectionPolicy = selectionPolicy;
    }

    public LogRecord parse(String line, String fileName) {
        ParserContext context = new ParserContext(line, fileName);
        if (context.trimmedLine().isEmpty()) {
            return null;
        }

        LogParser parser = selectionPolicy.select(parsers, context);
        return parser.parse(context);
    }
}
