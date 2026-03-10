package com.jw.log_analyzer.controller;

import com.jw.log_analyzer.dto.AnalysisResultDto;
import com.jw.log_analyzer.dto.AnalysisResultDto.TopServiceDto;
import com.jw.log_analyzer.service.LogAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LogAnalysisController.class)
class LogAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LogAnalysisService service;

    @Test
    void analyzeMaverEndpointReturnsJsonResponse() throws Exception {
        AnalysisResultDto result = new AnalysisResultDto(
                "a1b2",
                List.of(new TopServiceDto("news", 2L), new TopServiceDto("book", 1L)),
                Map.of("Chrome", 50.0, "Safari", 50.0)
        );
        when(service.analyze("logs/maver.log")).thenReturn(result);

        mockMvc.perform(get("/api/analyze/maver"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mostCalledApiKey").value("a1b2"))
                .andExpect(jsonPath("$.top3Services[0].serviceId").value("news"))
                .andExpect(jsonPath("$.top3Services[0].count").value(2))
                .andExpect(jsonPath("$.browserRatio.Chrome").value(50.0));

        verify(service).analyze("logs/maver.log");
    }
}
