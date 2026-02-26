package com.snapplayerapi.api.v2.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snapplayerapi.api.dto.ProcessingBatchResponse;
import com.snapplayerapi.api.dto.ProcessingFilmagemResponse;
import com.snapplayerapi.api.dto.ProcessingFrameResponse;
import com.snapplayerapi.api.dto.ProcessingSnapshotVideoResponse;
import com.snapplayerapi.api.dto.ProcessingVideoProbeResponse;
import com.snapplayerapi.api.v2.entity.SnapProcessingJobEntity;
import com.snapplayerapi.api.v2.repo.SnapProcessingJobRepository;
import com.snapplayerapi.api.v2.service.SnapProcessingGateway;
import com.snapplayerapi.api.v2.service.SnapProcessingJobMaintenanceService;
import com.snapplayerapi.api.v2.service.SnapProcessingJobWorker;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
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
 * Integration test for Entrega 4 async-create foundation.
 *
 * <p>The test enables async mode (`POST /v2/snaps` returns `PENDING` + enqueues DB job) while
 * keeping the scheduler disabled. It then triggers one worker cycle manually to validate the
 * end-to-end state transition to `COMPLETED`.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:snapv2async;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "app.snap.asyncCreateEnabled=true",
        "app.snap.workerEnabled=false",
        "app.snap.workerBatchSize=1",
        "app.snap.workerRetryDelaySeconds=1",
        "app.snap.workerRetryBackoffMultiplier=2.0",
        "app.snap.workerRetryMaxDelaySeconds=10",
        "app.snap.workerLockTimeoutSeconds=1",
        "app.snap.jobRetentionHours=1",
        "app.snap.jobCleanupBatchSize=10",
        "spring.jpa.hibernate.ddl-auto=none"
})
class SnapV2AsyncCreateIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SnapProcessingJobWorker snapProcessingJobWorker;

    @Autowired
    private SnapProcessingJobMaintenanceService snapProcessingJobMaintenanceService;

    @Autowired
    private SnapProcessingJobRepository snapProcessingJobRepository;

    @Test
    void shouldCreatePendingSnapAndCompleteAfterManualWorkerCycle() throws Exception {
        // Async create should persist a PENDING snap immediately, with processing outputs still empty.
        String createResponse = mockMvc.perform(post("/v2/snaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSnapBody("https://example.com/video-async.mp4", "operador-async", "async-1", "ASYNC-001", 455.0)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.frameCount").value(0))
                .andExpect(jsonPath("$.frames.length()").value(0))
                .andExpect(jsonPath("$.job.status").value("PENDING"))
                .andExpect(jsonPath("$.job.attempts").value(0))
                .andExpect(jsonPath("$.job.maxAttempts").value(3))
                .andExpect(jsonPath("$.processedAt").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(createResponse);
        String snapId = created.path("snapId").asText();

        // `GET` before worker execution should still show the queued state.
        mockMvc.perform(get("/v2/snaps/{snapId}", UUID.fromString(snapId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.job.status").value("PENDING"))
                .andExpect(jsonPath("$.frames.length()").value(0));

        // Manual deterministic worker cycle (scheduler disabled in this test).
        snapProcessingJobWorker.processPendingJobsOnce();

        mockMvc.perform(get("/v2/snaps/{snapId}", UUID.fromString(snapId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.job.status").value("COMPLETED"))
                .andExpect(jsonPath("$.job.attempts").value(1))
                .andExpect(jsonPath("$.videoProbe.compatible").value(true))
                .andExpect(jsonPath("$.snapshotVideo.fileName").value("snapshot.mp4"))
                .andExpect(jsonPath("$.frameCount").value(2))
                .andExpect(jsonPath("$.frames[0].fileName").value("frame_00001.jpg"));
    }

    @Test
    void shouldRetryWithBackoffThenCompleteAndExposeJobTelemetry() throws Exception {
        // The stub gateway fails once when subject id starts with `fail-once-`, then succeeds.
        String createResponse = mockMvc.perform(post("/v2/snaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSnapBody("https://example.com/video-retry.mp4", "operador-retry", "fail-once-1", "RETRY-001", 460.0)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID snapId = UUID.fromString(objectMapper.readTree(createResponse).path("snapId").asText());

        // First worker cycle fails and schedules retry with backoff (job remains non-terminal).
        snapProcessingJobWorker.processPendingJobsOnce();

        SnapProcessingJobEntity jobAfterFirstFailure = snapProcessingJobRepository.findBySnapId(snapId).orElseThrow();
        Assertions.assertEquals("RETRY_WAIT", jobAfterFirstFailure.getStatus());
        Assertions.assertEquals(1, jobAfterFirstFailure.getAttempts());
        Assertions.assertNotNull(jobAfterFirstFailure.getNextRunAt());
        Assertions.assertTrue(jobAfterFirstFailure.getNextRunAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1)));

        mockMvc.perform(get("/v2/snaps/{snapId}", snapId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.job.status").value("RETRY_WAIT"))
                .andExpect(jsonPath("$.job.attempts").value(1))
                .andExpect(jsonPath("$.error").isString());

        // Force immediate re-claim to keep the test deterministic (no scheduler wait).
        jobAfterFirstFailure.setNextRunAt(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        snapProcessingJobRepository.save(jobAfterFirstFailure);

        snapProcessingJobWorker.processPendingJobsOnce();

        mockMvc.perform(get("/v2/snaps/{snapId}", snapId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.job.status").value("COMPLETED"))
                .andExpect(jsonPath("$.job.attempts").value(2));

        mockMvc.perform(get("/internal/observability/snap-job-metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimedCount").isNumber())
                .andExpect(jsonPath("$.retryScheduledCount").isNumber())
                .andExpect(jsonPath("$.completedCount").isNumber())
                .andExpect(jsonPath("$.terminalByStatus.COMPLETED").isNumber());
    }

    @Test
    void shouldRecoverStaleRunningJobAndCleanupOldTerminalJobRows() throws Exception {
        String createResponse = mockMvc.perform(post("/v2/snaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSnapBody("https://example.com/video-stale.mp4", "operador-stale", "stale-1", "STALE-001", 470.0)))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID snapId = UUID.fromString(objectMapper.readTree(createResponse).path("snapId").asText());
        SnapProcessingJobEntity job = snapProcessingJobRepository.findBySnapId(snapId).orElseThrow();

        // Simulate a worker crash after claim: job stuck in RUNNING with an old lock timestamp.
        OffsetDateTime oldStartedAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);
        job.setStatus("RUNNING");
        job.setAttempts(1);
        job.setLockedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(3));
        job.setLockOwner("crashed-worker");
        job.setStartedAt(oldStartedAt);
        job.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(3));
        snapProcessingJobRepository.save(job);

        int recovered = snapProcessingJobWorker.recoverStaleRunningJobsOnce();
        Assertions.assertTrue(recovered >= 1);

        SnapProcessingJobEntity recoveredJob = snapProcessingJobRepository.findBySnapId(snapId).orElseThrow();
        Assertions.assertEquals("RETRY_WAIT", recoveredJob.getStatus());
        Assertions.assertNull(recoveredJob.getLockedAt());
        Assertions.assertNull(recoveredJob.getLockOwner());
        Assertions.assertNotNull(recoveredJob.getLastError());

        // Force immediate retry and complete successfully.
        recoveredJob.setNextRunAt(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        snapProcessingJobRepository.save(recoveredJob);
        snapProcessingJobWorker.processPendingJobsOnce();

        SnapProcessingJobEntity completedJob = snapProcessingJobRepository.findBySnapId(snapId).orElseThrow();
        Assertions.assertEquals("COMPLETED", completedJob.getStatus());
        Assertions.assertNotNull(completedJob.getFinishedAt());

        // Move terminal row outside retention window and trigger one cleanup cycle.
        completedJob.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));
        snapProcessingJobRepository.save(completedJob);
        int deleted = snapProcessingJobMaintenanceService.cleanupTerminalJobsOnce();
        Assertions.assertEquals(1, deleted);
        Assertions.assertTrue(snapProcessingJobRepository.findBySnapId(snapId).isEmpty());

        mockMvc.perform(get("/internal/observability/snap-job-metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staleRecoveredCount").isNumber())
                .andExpect(jsonPath("$.cleanupDeletedCount").isNumber());
    }

    private static String createSnapBody(String videoUrl, String nickname, String subjectId, String brinco, double peso) {
        // Local JSON fixture helper keeps the test focused on async state transitions.
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
        /**
         * Stateful counters keyed by `subject.id` so tests can simulate retry behavior deterministically
         * (fail first call, succeed on the next one) without external mocking frameworks.
         */
        private static final Map<String, AtomicInteger> SUBJECT_ATTEMPTS = new ConcurrentHashMap<>();

        @Bean
        @Primary
        SnapProcessingGateway snapProcessingGateway() {
            return request -> {
                OffsetDateTime now = OffsetDateTime.parse("2026-02-25T00:00:00Z");
                String subjectId = request.subject().id();
                if (subjectId != null && subjectId.startsWith("fail-once-")) {
                    int call = SUBJECT_ATTEMPTS.computeIfAbsent(subjectId, ignored -> new AtomicInteger()).incrementAndGet();
                    if (call == 1) {
                        throw new IllegalStateException("Simulated transient processing failure for retry test");
                    }
                }
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
