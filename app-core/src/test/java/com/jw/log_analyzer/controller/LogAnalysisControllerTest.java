package com.jw.log_analyzer.controller;

import com.jw.log_analyzer.dto.AnalysisResultDto;
import com.jw.log_analyzer.dto.AnalysisResultDto.TopServiceDto;
import com.jw.log_analyzer.exception.LogProcessingException;
import com.jw.log_analyzer.parser.contract.exception.InvalidLogFormatException;
import com.jw.log_analyzer.service.LogAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LogAnalysisController.class)
class LogAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LogAnalysisService service;

    @Test
    void analyzeEndpointReturnsJsonResponse() throws Exception {
        AnalysisResultDto result = new AnalysisResultDto(
                "a1b2",
                List.of(new TopServiceDto("news", 2L), new TopServiceDto("book", 1L)),
                Map.of("Chrome", 50.0, "Safari", 50.0)
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "upload.log",
                "text/plain",
                "[200][http://apis.kokoa.com/search/news?apikey=a1b2][Chrome][2018-06-10 08:00:00]".getBytes()
        );
        when(service.analyze(any())).thenReturn(result);

        mockMvc.perform(multipart("/api/analyze").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mostCalledApiKey").value("a1b2"))
                .andExpect(jsonPath("$.top3Services[0].serviceId").value("news"))
                .andExpect(jsonPath("$.top3Services[0].count").value(2))
                .andExpect(jsonPath("$.browserRatio.Chrome").value(50.0));

        verify(service).analyze(any());
    }

    @Test
    void analyzeEndpointReturnsBadRequestWhenLogFormatIsInvalid() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "upload.log",
                "text/plain",
                "invalid-format".getBytes()
        );
        when(service.analyze(any())).thenThrow(new InvalidLogFormatException("Unsupported log format at line 1"));

        mockMvc.perform(multipart("/api/analyze").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported log format at line 1"));
    }

    @Test
    void analyzeEndpointReturnsInternalServerErrorWhenInternalProcessingFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "upload.log",
                "text/plain",
                "invalid-format".getBytes()
        );
        when(service.analyze(any())).thenThrow(new LogProcessingException("Unexpected error while processing log at line 1",
                new IllegalStateException("boom")));

        mockMvc.perform(multipart("/api/analyze").file(file))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal error occurred while processing log file"));
    }
}
