package com.jw.log_analyzer.controller;

import com.jw.log_analyzer.service.LogAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LogAnalysisController.class)
class LogAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LogAnalysisService service;

    @Test
    void analyzeMaverEndpointReturnsDownloadResponse() throws Exception {
        Path temp = Files.createTempFile("maver-result-", ".txt");
        when(service.analyzeAndWriteToFile(eq("logs/maver.log"), eq("maver-result-"))).thenReturn(temp);

        mockMvc.perform(get("/api/analyze/maver"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain; charset=UTF-8"));

        verify(service).analyzeAndWriteToFile("logs/maver.log", "maver-result-");
        Files.deleteIfExists(temp);
    }
}
