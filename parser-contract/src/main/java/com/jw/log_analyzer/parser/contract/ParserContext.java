package com.jw.log_analyzer.parser.contract;

public record ParserContext(String line, String fileName) {

    public String trimmedLine() {
        return line == null ? "" : line.trim();
    }
}
