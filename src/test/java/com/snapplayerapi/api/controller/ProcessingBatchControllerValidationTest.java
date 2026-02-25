package com.snapplayerapi.api.controller;

import com.snapplayerapi.api.dto.ProcessingBatchResponse;
import com.snapplayerapi.api.service.ProcessingVideoFrameService;
import com.snapplayerapi.api.web.GlobalExceptionHandler;
import com.snapplayerapi.api.web.HttpObservabilityRegistry;
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

@WebMvcTest(ProcessingBatchController.class)
@Import(GlobalExceptionHandler.class)
class ProcessingBatchControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProcessingVideoFrameService processingVideoFrameService;

    /**
     * `@WebMvcTest` loads MVC infrastructure (including the observability interceptor) but not all
     * application components. The interceptor depends on this registry, so we provide a test mock to
     * keep the controller slice isolated.
     */
    @MockBean
    private HttpObservabilityRegistry httpObservabilityRegistry;

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

        mockMvc.perform(post("/v1/video-frames/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void shouldAcceptValidRequest() throws Exception {
        when(processingVideoFrameService.process(anyList())).thenReturn(new ProcessingBatchResponse(
                "req-1",
                "COMPLETED",
                "/tmp/processing",
                "/tmp/processing/req-1",
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

        mockMvc.perform(post("/v1/video-frames/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
