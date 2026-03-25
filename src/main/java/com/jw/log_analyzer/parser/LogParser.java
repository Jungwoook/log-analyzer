package com.jw.log_analyzer.parser;

import com.jw.log_analyzer.dto.LogEntryDto;

public interface LogParser {

    boolean supports(String line);

    LogEntryDto parse(String line);
}
