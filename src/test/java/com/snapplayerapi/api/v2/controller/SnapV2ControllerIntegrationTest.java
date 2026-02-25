package com.snapplayerapi.api.v2.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snapplayerapi.api.dto.ProcessingBatchResponse;
import com.snapplayerapi.api.dto.ProcessingFilmagemRequest;
import com.snapplayerapi.api.dto.ProcessingFilmagemResponse;
import com.snapplayerapi.api.dto.ProcessingFrameResponse;
import com.snapplayerapi.api.dto.ProcessingSnapshotVideoResponse;
import com.snapplayerapi.api.dto.ProcessingVideoProbeResponse;
import com.snapplayerapi.api.v2.service.SnapProcessingGateway;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Entregas 1-2 `v2` endpoints.
 *
 * <p>These tests exercise controller + service + persistence (Flyway/H2) end-to-end while replacing
 * only the processing gateway with a deterministic stub. This keeps the tests stable and fast
 * without requiring FFmpeg binaries or remote video downloads.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:snapv2test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class SnapV2ControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateSnapFallbackSubjectIdAndFetchIt() throws Exception {
        // Request omits `subject.id` on purpose to validate ADR-0004 fallback (`subject.id = snapId`).
        String createBody = """
                {
                  "videoUrl": "https://example.com/video-a.mp4",
                  "nickname": "operador1",
                  "email": "operador1@example.com",
                  "dataFilmagem": "2026-02-24T14:30:00-03:00",
                  "startFrame": 360,
                  "durationSeconds": 1.0,
                  "snapshotDurationSeconds": 2.0,
                  "fps": 5,
                  "maxWidth": 640,
                  "format": "jpg",
                  "quality": 8,
                  "subject": {
                    "attributes": [
                      { "key": "brinco", "type": "string", "stringValue": "123" },
                      { "key": "peso", "type": "number", "numberValue": 450.0 }
                    ]
                  }
                }
                """;

        String createResponse = mockMvc.perform(post("/v2/snaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.subject.id").isString())
                .andExpect(jsonPath("$.subjectTemplateId").isNumber())
                .andExpect(jsonPath("$.snapshotVideo.fileName").value("snapshot.mp4"))
                .andExpect(jsonPath("$.frames[0].fileName").value("frame_00001.jpg"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(createResponse);
        String snapId = created.path("snapId").asText();
        String subjectId = created.path("subject").path("id").asText();

        // Entrega 1 rule / ADR-0004: subject.id falls back to snapId when omitted by the client.
        Assertions.assertEquals(snapId, subjectId);

        mockMvc.perform(get("/v2/snaps/{snapId}", UUID.fromString(snapId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapId").value(snapId))
                .andExpect(jsonPath("$.subject.id").value(snapId))
                .andExpect(jsonPath("$.videoProbe.compatible").value(true));
    }

    @Test
    void shouldAcceptExplicitAssinaturaHeaderWithDefaultCodigo() throws Exception {
        // Entrega 3 (step 1): tenant context is formalized via optional header, while `default` remains valid.
        mockMvc.perform(post("/v2/snaps")
                        .header("X-Assinatura-Codigo", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSnapBody("https://example.com/video-header-default.mp4", "operador-header", "h-1", "HDR-1", 440.0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.nickname").value("operador-header"));
    }

    @Test
    void shouldListByVideoAndSearchBySubjectAndAttribute() throws Exception {
        // Create two snaps on the same URL to validate video reuse + list and search behavior.
        String firstCreateResponse = mockMvc.perform(post("/v2/snaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSnapBody("https://example.com/video-b.mp4", "operador1", "a-1", "123", 450.0)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode first = objectMapper.readTree(firstCreateResponse);
        String videoId = first.path("videoId").asText();

        mockMvc.perform(post("/v2/snaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSnapBody("https://example.com/video-b.mp4", "operador2", "a-2", "999", 470.0)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v2/videos/{videoId}/snaps", UUID.fromString(videoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId").value(videoId))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.page.offset").value(0))
                .andExpect(jsonPath("$.page.limit").value(50))
                .andExpect(jsonPath("$.page.sortBy").value("resolvedStartSeconds"))
                .andExpect(jsonPath("$.page.sortDir").value("asc"))
                .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(get("/v2/videos/{videoId}/snaps", UUID.fromString(videoId))
                        .queryParam("nickname", "operador1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].nickname").value("operador1"));

        mockMvc.perform(get("/v2/snaps/search")
                        .queryParam("subjectId", "a-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[0].subject.id").value("a-1"));

        mockMvc.perform(get("/v2/snaps/search")
                        .queryParam("attrKey", "brinco")
                        .queryParam("attrValue", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page.returned").value(1))
                .andExpect(jsonPath("$.page.sortBy").value("createdAt"))
                .andExpect(jsonPath("$.page.sortDir").value("desc"))
                .andExpect(jsonPath("$.items[0].subject.attributes[0].stringValue").value("999"));
    }

    @Test
    void shouldShareSnapAndFetchPublicView() throws Exception {
        // Entrega 2 share/public flow: create -> share (idempotent) -> fetch public token.
        String createResponse = mockMvc.perform(post("/v2/snaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSnapBody("https://example.com/video-share.mp4", "operador-share", "s-1", "SH-1", 410.0)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String snapId = objectMapper.readTree(createResponse).path("snapId").asText();

        String shareResponse = mockMvc.perform(post("/v2/snaps/{snapId}/share", UUID.fromString(snapId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapId").value(snapId))
                .andExpect(jsonPath("$.isPublic").value(true))
                .andExpect(jsonPath("$.publicShareToken").isString())
                .andExpect(jsonPath("$.publicUrl").value(org.hamcrest.Matchers.startsWith("/public/snaps/")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = objectMapper.readTree(shareResponse).path("publicShareToken").asText();

        // Repeated share call should keep the same token (idempotent behavior).
        mockMvc.perform(post("/v2/snaps/{snapId}/share", UUID.fromString(snapId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicShareToken").value(token));

        mockMvc.perform(get("/public/snaps/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapId").value(snapId))
                .andExpect(jsonPath("$.publicShareToken").value(token))
                .andExpect(jsonPath("$.nickname").value("operador-share"))
                .andExpect(jsonPath("$.snapshotVideo.fileName").value("snapshot.mp4"))
                .andExpect(jsonPath("$.frames[0].fileName").value("frame_00001.jpg"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.outputDir").doesNotExist());
    }

    @Test
    void shouldListMineSnapsAndMineVideos() throws Exception {
        // Create mixed activity to validate nickname filtering and video aggregation counts.
        mockMvc.perform(post("/v2/snaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSnapBody("https://example.com/video-m1.mp4", "operador-mine", "m-1", "M-001", 430.0)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/v2/snaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSnapBody("https://example.com/video-m1.mp4", "operador-mine", "m-2", "M-002", 431.0)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/v2/snaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSnapBody("https://example.com/video-m2.mp4", "operador-mine", "m-3", "M-003", 432.0)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/v2/snaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSnapBody("https://example.com/video-m3.mp4", "outro-usuario", "x-1", "X-001", 500.0)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v2/snaps/mine").queryParam("nickname", "operador-mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("operador-mine"))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(3));

        String mineVideosResponse = mockMvc.perform(get("/v2/videos/mine").queryParam("nickname", "operador-mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("operador-mine"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode mineVideos = objectMapper.readTree(mineVideosResponse);
        List<Integer> snapCounts = List.of(
                mineVideos.path("items").get(0).path("snapCount").asInt(),
                mineVideos.path("items").get(1).path("snapCount").asInt()
        );
        // Order is by most recent activity, so assert the multiset of counts instead of positions.
        Assertions.assertTrue(snapCounts.contains(2));
        Assertions.assertTrue(snapCounts.contains(1));
    }

    @Test
    void shouldPaginateAndSortMineEndpointsAndValidateInvalidSort() throws Exception {
        // Prepare 3 snaps across 2 videos so pagination and aggregate sorting have deterministic values.
        mockMvc.perform(post("/v2/snaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSnapBody("https://example.com/video-p1.mp4", "operador-page", "p-1", "P-001", 430.0)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/v2/snaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSnapBody("https://example.com/video-p1.mp4", "operador-page", "p-2", "P-002", 431.0)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/v2/snaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSnapBody("https://example.com/video-p2.mp4", "operador-page", "p-3", "P-003", 432.0)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v2/snaps/mine")
                        .queryParam("nickname", "operador-page")
                        .queryParam("offset", "1")
                        .queryParam("limit", "1")
                        .queryParam("sortBy", "createdAt")
                        .queryParam("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("operador-page"))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page.offset").value(1))
                .andExpect(jsonPath("$.page.limit").value(1))
                .andExpect(jsonPath("$.page.returned").value(1))
                .andExpect(jsonPath("$.page.hasMore").value(true))
                .andExpect(jsonPath("$.page.sortBy").value("createdAt"))
                .andExpect(jsonPath("$.page.sortDir").value("desc"))
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/v2/videos/mine")
                        .queryParam("nickname", "operador-page")
                        .queryParam("sortBy", "snapCount")
                        .queryParam("sortDir", "desc")
                        .queryParam("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.page.offset").value(0))
                .andExpect(jsonPath("$.page.limit").value(1))
                .andExpect(jsonPath("$.page.returned").value(1))
                .andExpect(jsonPath("$.page.hasMore").value(true))
                .andExpect(jsonPath("$.page.sortBy").value("snapCount"))
                .andExpect(jsonPath("$.page.sortDir").value("desc"))
                .andExpect(jsonPath("$.items[0].snapCount").value(2));

        mockMvc.perform(get("/v2/snaps/search")
                        .queryParam("subjectId", "p-1")
                        .queryParam("sortBy", "invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Unsupported sortBy: invalido")));
    }

    @Test
    void shouldExposeRequestIdHeaderAndInternalHttpMetrics() throws Exception {
        // Any API call should return a correlation header even when the client does not provide one.
        mockMvc.perform(get("/v2/snaps/search").queryParam("subjectId", "nao-existe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("X-Request-Id"));

        // Internal observability endpoint should expose aggregated route metrics for HTTP traffic.
        mockMvc.perform(get("/internal/observability/http-metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").isString())
                .andExpect(jsonPath("$.totalRequests", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.routesCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.routes[?(@.routePattern == '/v2/snaps/search')].requests").exists());
    }

    @Test
    void shouldReturn400WhenCreateSnapHasNoVideoIdOrVideoUrl() throws Exception {
        // Service-level validation is translated by the global exception handler to HTTP 400.
        String body = """
                {
                  "nickname": "operador1",
                  "email": "operador1@example.com",
                  "dataFilmagem": "2026-02-24T14:30:00-03:00",
                  "startSeconds": 12.0,
                  "durationSeconds": 1.0,
                  "subject": { "id": "a-1", "attributes": [] }
                }
                """;

        mockMvc.perform(post("/v2/snaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Provide at least one of videoId or videoUrl"));
    }

    @Test
    void shouldReturn400WhenCreateSnapHasInvalidEmail() throws Exception {
        // Bean validation (`@Email`) should fail before reaching the service.
        String body = """
                {
                  "videoUrl": "https://example.com/video.mp4",
                  "nickname": "operador1",
                  "email": "email-invalido",
                  "dataFilmagem": "2026-02-24T14:30:00-03:00",
                  "startSeconds": 12.0,
                  "durationSeconds": 1.0,
                  "subject": { "id": "a-1", "attributes": [] }
                }
                """;

        mockMvc.perform(post("/v2/snaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void shouldReturn404WhenSnapDoesNotExist() throws Exception {
        mockMvc.perform(get("/v2/snaps/{snapId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Snap not found:")));
    }

    @Test
    void shouldReturn404WhenAssinaturaHeaderDoesNotExist() throws Exception {
        // The request context is now explicit; unknown tenant code should fail before endpoint logic proceeds.
        mockMvc.perform(get("/v2/snaps/search")
                        .header("X-Assinatura-Codigo", "assinatura-inexistente")
                        .queryParam("subjectId", "qualquer"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Assinatura not found: assinatura-inexistente"));
    }

    @Test
    void shouldReturn404WhenPublicTokenDoesNotExist() throws Exception {
        mockMvc.perform(get("/public/snaps/{token}", "token-inexistente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Public snap not found:")));
    }

    @Test
    void shouldReturn400WhenSearchHasOnlyAttrKey() throws Exception {
        mockMvc.perform(get("/v2/snaps/search")
                        .queryParam("attrKey", "brinco"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Provide attrKey and attrValue together"));
    }

    @Test
    void shouldReturn400WhenSearchHasNoFilters() throws Exception {
        mockMvc.perform(get("/v2/snaps/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Provide subjectId or attrKey+attrValue"));
    }

    @Test
    void shouldReturn400WhenMineNicknameIsBlank() throws Exception {
        // `@NotBlank` on query params is handled by method validation.
        mockMvc.perform(get("/v2/snaps/mine").queryParam("nickname", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    private static String createSnapBody(String videoUrl, String nickname, String subjectId, String brinco, double peso) {
        // Utility builder keeps JSON fixtures compact and highlights scenario-specific values.
        return """
                {
                  "videoUrl": "%s",
                  "nickname": "%s",
                  "email": "%s@example.com",
                  "dataFilmagem": "2026-02-24T14:30:00-03:00",
                  "startSeconds": 12.0,
                  "durationSeconds": 1.0,
                  "snapshotDurationSeconds": 2.0,
                  "fps": 5,
                  "maxWidth": 640,
                  "format": "jpg",
                  "quality": 8,
                  "subject": {
                    "id": "%s",
                    "attributes": [
                      { "key": "brinco", "type": "string", "stringValue": "%s" },
                      { "key": "peso", "type": "number", "numberValue": %s }
                    ]
                  }
                }
                """.formatted(videoUrl, nickname, nickname, subjectId, brinco, peso);
    }

    @TestConfiguration
    static class StubProcessingConfig {
        @Bean
        @Primary
        SnapProcessingGateway snapProcessingGateway() {
            return request -> {
                // Deterministic stub output that mirrors the fields the `v2` service persists/returns.
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
