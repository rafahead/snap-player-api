package com.oddplayerapi.mvp.controller;

import com.oddplayerapi.mvp.dto.MvpBatchResponse;
import com.oddplayerapi.mvp.service.MvpVideoFrameService;
import com.oddplayerapi.mvp.web.GlobalExceptionHandler;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MvpBatchController.class)
@Import(GlobalExceptionHandler.class)
class MvpBatchControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MvpVideoFrameService mvpVideoFrameService;

    @Test
    void shouldReturn400WhenVideoUrlIsMissing() throws Exception {
        String body = """
                [
                  {
                    "startSeconds": 0.0,
                    "durationSeconds": 1.0,
                    "dataFilmagem": "2026-02-24T14:30:00-03:00",
                    "subject": { "id": "animal-123", "attributes": [] }
                  }
                ]
                """;

        mockMvc.perform(post("/v1/video-frames/mvp/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void shouldAcceptValidRequest() throws Exception {
        when(mvpVideoFrameService.process(anyList())).thenReturn(new MvpBatchResponse(
                "req-1",
                "COMPLETED",
                "/tmp/mvp",
                "/tmp/mvp/req-1",
                OffsetDateTime.parse("2026-02-25T00:00:00Z"),
                List.of()
        ));

        String body = """
                [
                  {
                    "videoUrl": "https://example.com/video.mp4",
                    "startSeconds": 0.0,
                    "durationSeconds": 1.0,
                    "dataFilmagem": "2026-02-24T14:30:00-03:00",
                    "subject": { "id": "animal-123", "attributes": [] }
                  }
                ]
                """;

        mockMvc.perform(post("/v1/video-frames/mvp/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
