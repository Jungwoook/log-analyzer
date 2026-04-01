package com.jw.log_analyzer.parser.contract;

public interface LogParser {

    boolean supports(ParserContext context);

    default boolean supportsFileName(String fileName) {
        return false;
    }

    String sourceType();

    LogRecord parse(ParserContext context);
}
