package com.snapplayerapi.api.v2.controller;

import com.snapplayerapi.api.dto.ProcessingBatchResponse;
import com.snapplayerapi.api.dto.ProcessingFilmagemResponse;
import com.snapplayerapi.api.dto.ProcessingFrameResponse;
import com.snapplayerapi.api.dto.ProcessingSnapshotVideoResponse;
import com.snapplayerapi.api.dto.ProcessingVideoProbeResponse;
import com.snapplayerapi.api.v2.service.SnapProcessingGateway;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the optional assinatura token auth feature flag (Entrega 3 step 2).
 *
 * <p>These tests verify only the guard behavior on private `/v2/*` routes. Public routes remain
 * accessible without token, but that behavior is already covered indirectly by the share/public
 * tests in the main `v2` integration suite when the flag is disabled.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:snapv2tokenauth;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "app.snap.requireApiToken=true",
        "spring.jpa.hibernate.ddl-auto=none",
        // Pin sync mode: this suite tests token auth + sync create (201 Created).
        // asyncCreateEnabled defaults to true in application.yml since Slice 6 rollout.
        "app.snap.asyncCreateEnabled=false"
})
class SnapV2TokenAuthIntegrationTest {

    private static final String TOKEN_HEADER = "X-Assinatura-Token";
    private static final String ASSINATURA_HEADER = "X-Assinatura-Codigo";
    private static final String DEFAULT_TOKEN = "dev-default-token";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturn401WhenPrivateRouteHasNoTokenAndFlagIsEnabled() throws Exception {
        mockMvc.perform(get("/v2/snaps/search").queryParam("subjectId", "qualquer"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Assinatura API token required"));
    }

    @Test
    void shouldReturn401WhenPrivateRouteHasInvalidToken() throws Exception {
        mockMvc.perform(get("/v2/snaps/search")
                        .header(TOKEN_HEADER, "token-invalido")
                        .queryParam("subjectId", "qualquer"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid assinatura API token"));
    }

    @Test
    void shouldAllowPrivateRouteWhenTokenMatchesAssinatura() throws Exception {
        mockMvc.perform(get("/v2/snaps/search")
                        .header(TOKEN_HEADER, DEFAULT_TOKEN)
                        .queryParam("subjectId", "qualquer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void shouldCreateSnapWithExplicitAssinaturaHeaderAndValidToken() throws Exception {
        String body = """
                {
                  "videoUrl": "https://example.com/video-token-auth.mp4",
                  "nickname": "token-user",
                  "email": "token-user@example.com",
                  "dataFilmagem": "2026-02-24T14:30:00-03:00",
                  "startSeconds": 12.0,
                  "durationSeconds": 1.0,
                  "snapshotDurationSeconds": 2.0,
                  "fps": 5,
                  "maxWidth": 640,
                  "format": "jpg",
                  "quality": 8,
                  "subject": {
                    "id": "token-subject-1",
                    "attributes": [
                      { "key": "brinco", "type": "string", "stringValue": "TK-001" }
                    ]
                  }
                }
                """;

        mockMvc.perform(post("/v2/snaps")
                        .header(ASSINATURA_HEADER, "default")
                        .header(TOKEN_HEADER, DEFAULT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.nickname").value("token-user"));
    }

    @TestConfiguration
    static class StubProcessingConfig {
        @Bean
        @Primary
        SnapProcessingGateway snapProcessingGateway() {
            return request -> {
                OffsetDateTime now = OffsetDateTime.parse("2026-02-25T00:00:00Z");
                double resolvedStart = request.startFrame() != null ? 12.0 : (request.startSeconds() == null ? 0.0 : request.startSeconds());
                double snapshotDuration = request.snapshotDurationSeconds() == null ? request.durationSeconds() : request.snapshotDurationSeconds();

                ProcessingFilmagemResponse item = new ProcessingFilmagemResponse(
                        0,
                        "SUCCEEDED",
                        request.dataFilmagem().toString(),
                        request.subject(),
                        request.videoUrl(),
                        request.startSeconds(),
                        request.startFrame(),
                        resolvedStart,
                        new ProcessingVideoProbeResponse(true, "mov,mp4,m4a,3gp,3g2,mj2", "h264", 1280, 720, 18.4, 29.97, "yuv420p", null),
                        "/tmp/video-frames-processing/req-1/item-000",
                        new ProcessingSnapshotVideoResponse("snapshot.mp4", "/tmp/video-frames-processing/req-1/item-000/snapshot.mp4", snapshotDuration),
                        2,
                        List.of(
                                new ProcessingFrameResponse(1, resolvedStart, "frame_00001.jpg", "/tmp/video-frames-processing/req-1/item-000/frame_00001.jpg"),
                                new ProcessingFrameResponse(2, resolvedStart + (1.0 / 5.0), "frame_00002.jpg", "/tmp/video-frames-processing/req-1/item-000/frame_00002.jpg")
                        ),
                        null
                );
                return new ProcessingBatchResponse("req-1", "COMPLETED", "/tmp/video-frames-processing", "/tmp/video-frames-processing/req-1", now, List.of(item));
            };
        }
    }
}
