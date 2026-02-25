package com.snapplayerapi.api.v2.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snapplayerapi.api.dto.ProcessingBatchResponse;
import com.snapplayerapi.api.dto.ProcessingFilmagemRequest;
import com.snapplayerapi.api.dto.ProcessingFilmagemResponse;
import com.snapplayerapi.api.dto.ProcessingFrameResponse;
import com.snapplayerapi.api.dto.ProcessingOverlayRequest;
import com.snapplayerapi.api.dto.ProcessingSnapshotVideoResponse;
import com.snapplayerapi.api.dto.ProcessingSubjectAttributeRequest;
import com.snapplayerapi.api.dto.ProcessingSubjectRequest;
import com.snapplayerapi.api.dto.ProcessingVideoProbeResponse;
import com.snapplayerapi.api.v2.config.SnapProperties;
import com.snapplayerapi.api.v2.dto.CreateSnapRequest;
import com.snapplayerapi.api.v2.dto.MineSnapsResponse;
import com.snapplayerapi.api.v2.dto.MineVideoItemResponse;
import com.snapplayerapi.api.v2.dto.MineVideosResponse;
import com.snapplayerapi.api.v2.dto.PublicSnapResponse;
import com.snapplayerapi.api.v2.dto.ShareSnapResponse;
import com.snapplayerapi.api.v2.dto.SnapResponse;
import com.snapplayerapi.api.v2.dto.SnapSearchResponse;
import com.snapplayerapi.api.v2.dto.V2SubjectRequest;
import com.snapplayerapi.api.v2.dto.VideoSnapsResponse;
import com.snapplayerapi.api.v2.entity.AssinaturaEntity;
import com.snapplayerapi.api.v2.entity.SnapEntity;
import com.snapplayerapi.api.v2.entity.SnapSubjectAttrEntity;
import com.snapplayerapi.api.v2.entity.SubjectTemplateEntity;
import com.snapplayerapi.api.v2.entity.UsuarioEntity;
import com.snapplayerapi.api.v2.entity.VideoEntity;
import com.snapplayerapi.api.v2.repo.AssinaturaRepository;
import com.snapplayerapi.api.v2.repo.SnapRepository;
import com.snapplayerapi.api.v2.repo.SnapSubjectAttrRepository;
import com.snapplayerapi.api.v2.repo.SubjectTemplateRepository;
import com.snapplayerapi.api.v2.repo.UsuarioRepository;
import com.snapplayerapi.api.v2.repo.VideoRepository;
import com.snapplayerapi.api.web.UnauthorizedException;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Application service for Entregas 1-2 (`v2`) Snap-first endpoints.
 *
 * <p>This service keeps the initial behavior intentionally synchronous:
 * it resolves tenancy/template/video metadata, delegates processing to the existing MVP pipeline,
 * persists the generated artifacts/metadata in the new Snap-first schema, and returns `v2`
 * responses immediately. Entrega 2 extends the same service with public sharing and "mine" lists.
 * The async worker architecture defined in `master-tecnico.md` is a later delivery and should
 * reuse this domain mapping logic where possible.</p>
 */
@Service
public class SnapV2Service {

    private static final TypeReference<List<ProcessingFrameResponse>> FRAMES_TYPE = new TypeReference<>() {};

    private final SnapProperties snapProperties;
    private final SnapProcessingGateway snapProcessingGateway;
    private final ObjectMapper objectMapper;
    private final AssinaturaRepository assinaturaRepository;
    private final SubjectTemplateRepository subjectTemplateRepository;
    private final UsuarioRepository usuarioRepository;
    private final VideoRepository videoRepository;
    private final SnapRepository snapRepository;
    private final SnapSubjectAttrRepository snapSubjectAttrRepository;

    public SnapV2Service(
            SnapProperties snapProperties,
            SnapProcessingGateway snapProcessingGateway,
            ObjectMapper objectMapper,
            AssinaturaRepository assinaturaRepository,
            SubjectTemplateRepository subjectTemplateRepository,
            UsuarioRepository usuarioRepository,
            VideoRepository videoRepository,
            SnapRepository snapRepository,
            SnapSubjectAttrRepository snapSubjectAttrRepository
    ) {
        this.snapProperties = snapProperties;
        this.snapProcessingGateway = snapProcessingGateway;
        this.objectMapper = objectMapper;
        this.assinaturaRepository = assinaturaRepository;
        this.subjectTemplateRepository = subjectTemplateRepository;
        this.usuarioRepository = usuarioRepository;
        this.videoRepository = videoRepository;
        this.snapRepository = snapRepository;
        this.snapSubjectAttrRepository = snapSubjectAttrRepository;
    }

    /**
     * Creates a snap synchronously (Entrega 1).
     *
     * <p>Flow summary:</p>
     * <p>1) Validate request invariants required by the `v2` contract.</p>
     * <p>2) Resolve request assinatura context (fallback to configured default) and user/template/video references.</p>
     * <p>3) Build an MVP request and execute the existing processing pipeline.</p>
     * <p>4) Persist the snap row plus flattened searchable subject attributes.</p>
     * <p>5) Return the persisted representation mapped to the public `v2` response DTO.</p>
     */
    @Transactional
    public SnapResponse createSnap(String assinaturaCodigo, String assinaturaToken, CreateSnapRequest request) {
        if ((request.videoId() == null || request.videoId().isBlank()) && (request.videoUrl() == null || request.videoUrl().isBlank())) {
            throw new IllegalArgumentException("Provide at least one of videoId or videoUrl");
        }
        if (request.startSeconds() == null && request.startFrame() == null) {
            throw new IllegalArgumentException("Provide at least one of startSeconds or startFrame");
        }
        if (request.subject() == null) {
            throw new IllegalArgumentException("subject must be provided");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID snapId = UUID.randomUUID();

        // Entrega 3 step 1: request context is explicit, but still defaults to the configured tenant.
        AssinaturaEntity assinatura = loadAssinatura(assinaturaCodigo, assinaturaToken);

        // User and template are resolved before processing so the snap snapshot reflects inputs used.
        UsuarioEntity usuario = resolveUsuario(request.nickname(), request.email(), now);
        SubjectTemplateEntity template = resolveTemplate(assinatura.getId(), request.subjectTemplateId());
        VideoEntity video = resolveOrCreateVideo(assinatura.getId(), usuario.getId(), request.videoId(), request.videoUrl(), now);

        // Apply ADR-0004 fallback: if the client omits subject.id, use the generated snap id.
        ProcessingSubjectRequest effectiveSubject = buildEffectiveSubject(snapId, request.subject());
        ProcessingFilmagemRequest mvpRequest = new ProcessingFilmagemRequest(
                video.getOriginalUrl(),
                request.startSeconds(),
                request.startFrame(),
                request.durationSeconds(),
                request.snapshotDurationSeconds(),
                request.fps(),
                request.maxWidth(),
                request.format(),
                request.quality(),
                request.dataFilmagem(),
                effectiveSubject,
                request.overlay(),
                null
        );

        // Entrega 1 remains synchronous and reuses the MVP implementation via gateway abstraction.
        ProcessingBatchResponse mvpBatchResponse = snapProcessingGateway.processSingle(mvpRequest);
        ProcessingFilmagemResponse item = mvpBatchResponse.filmagens().get(0);

        // Persist probe at video level so later snaps over the same URL can reuse metadata/history.
        if (item.videoProbe() != null) {
            video.setVideoProbeJson(writeJson(item.videoProbe()));
            videoRepository.save(video);
        }

        // Persist the full snap execution snapshot (request + processing result summary + artifacts).
        SnapEntity snap = new SnapEntity();
        snap.setId(snapId);
        snap.setAssinaturaId(assinatura.getId());
        snap.setVideoId(video.getId());
        snap.setCreatedByUsuarioId(usuario.getId());
        snap.setSubjectTemplateId(template.getId());
        snap.setNicknameSnapshot(usuario.getNickname());
        snap.setEmailSnapshot(usuario.getEmail());
        snap.setTipoSnap(resolveTipoSnap(request));
        snap.setStatus(mapSnapStatus(item.status()));
        snap.setPublic(false);
        snap.setDataFilmagem(request.dataFilmagem());
        snap.setVideoUrl(video.getOriginalUrl());
        snap.setStartSeconds(request.startSeconds());
        snap.setStartFrame(request.startFrame());
        snap.setResolvedStartSeconds(item.resolvedStartSeconds());
        snap.setDurationSeconds(request.durationSeconds());
        snap.setSnapshotDurationSeconds(item.snapshotVideo() != null ? item.snapshotVideo().durationSeconds() : effectiveSnapshotDuration(request));
        snap.setFps(defaultInt(request.fps(), 5));
        snap.setMaxWidth(defaultInt(request.maxWidth(), 1280));
        snap.setFormat(defaultFormat(request.format()));
        snap.setQuality(snap.getFormat().equals("jpg") ? defaultInt(request.quality(), 3) : null);
        snap.setSubjectId(effectiveSubject.id());
        snap.setSubjectJson(writeJson(toV2Subject(effectiveSubject)));
        snap.setOverlayJson(writeJsonOrNull(request.overlay()));
        snap.setVideoProbeJson(writeJsonOrNull(item.videoProbe()));
        snap.setSnapshotVideoJson(writeJsonOrNull(item.snapshotVideo()));
        snap.setFramesJson(writeJson(item.frames()));
        snap.setFrameCount(item.frameCount());
        snap.setOutputDir(item.outputDir());
        snap.setErrorMessage(item.error());
        snap.setCreatedAt(now);
        snap.setUpdatedAt(now);
        snap.setProcessedAt(now);
        snapRepository.save(snap);

        // Persist flattened attributes in a search-oriented table for Entrega 1 basic queries.
        persistSubjectAttributes(snap, effectiveSubject, now);
        return toResponse(snap);
    }

    /**
     * Returns one snap constrained to the active phase-1 assinatura.
     */
    public SnapResponse getSnap(String assinaturaCodigo, String assinaturaToken, UUID snapId) {
        AssinaturaEntity assinatura = loadAssinatura(assinaturaCodigo, assinaturaToken);
        SnapEntity snap = snapRepository.findByIdAndAssinaturaId(snapId, assinatura.getId())
                .orElseThrow(() -> new NoSuchElementException("Snap not found: " + snapId));
        return toResponse(snap);
    }

    /**
     * Enables public sharing for a snap and returns the share token/url.
     *
     * <p>The operation is idempotent: an already shared snap returns the same token. This keeps the
     * endpoint simple for clients that may call "share" more than once while editing/previewing.</p>
     */
    @Transactional
    public ShareSnapResponse shareSnap(String assinaturaCodigo, String assinaturaToken, UUID snapId) {
        AssinaturaEntity assinatura = loadAssinatura(assinaturaCodigo, assinaturaToken);
        SnapEntity snap = snapRepository.findByIdAndAssinaturaId(snapId, assinatura.getId())
                .orElseThrow(() -> new NoSuchElementException("Snap not found: " + snapId));

        if (!hasText(snap.getPublicShareToken())) {
            snap.setPublicShareToken(generateShareToken());
        }
        snap.setPublic(true);
        snap.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        snapRepository.save(snap);

        return new ShareSnapResponse(
                snap.getId(),
                snap.isPublic(),
                snap.getPublicShareToken(),
                buildPublicUrl(snap.getPublicShareToken())
        );
    }

    /**
     * Returns the public-safe representation for a shared snap.
     */
    public PublicSnapResponse getPublicSnap(String token) {
        if (!hasText(token)) {
            throw new IllegalArgumentException("token must be provided");
        }
        SnapEntity snap = snapRepository.findByPublicShareTokenAndIsPublicTrue(token.strip())
                .orElseThrow(() -> new NoSuchElementException("Public snap not found: " + token));
        return toPublicResponse(snap);
    }

    /**
     * Lists snaps for a video, optionally filtered by nickname, always scoped to the active assinatura.
     */
    public VideoSnapsResponse listSnapsByVideo(String assinaturaCodigo, String assinaturaToken, UUID videoId, String nickname) {
        AssinaturaEntity assinatura = loadAssinatura(assinaturaCodigo, assinaturaToken);
        List<SnapEntity> snaps;
        if (nickname == null || nickname.isBlank()) {
            snaps = snapRepository.findByVideoIdAndAssinaturaIdOrderByResolvedStartSecondsAscCreatedAtAsc(videoId, assinatura.getId());
        } else {
            snaps = snapRepository.findByVideoIdAndAssinaturaIdAndNicknameSnapshotIgnoreCaseOrderByResolvedStartSecondsAscCreatedAtAsc(
                    videoId,
                    assinatura.getId(),
                    nickname.strip()
            );
        }
        List<SnapResponse> items = snaps.stream().map(this::toResponse).toList();
        return new VideoSnapsResponse(videoId, items.size(), items);
    }

    /**
     * Basic Entrega 1 search:
     * by `subjectId` and/or exact string attribute (`attrKey` + `attrValue`).
     */
    public SnapSearchResponse search(String assinaturaCodigo, String assinaturaToken, String subjectId, String attrKey, String attrValue) {
        AssinaturaEntity assinatura = loadAssinatura(assinaturaCodigo, assinaturaToken);
        String normalizedSubjectId = hasText(subjectId) ? subjectId.strip() : null;
        List<SnapEntity> snaps;

        boolean hasAttrKey = hasText(attrKey);
        boolean hasAttrValue = hasText(attrValue);
        if (hasAttrKey != hasAttrValue) {
            throw new IllegalArgumentException("Provide attrKey and attrValue together");
        }
        if (!hasText(subjectId) && !hasAttrKey) {
            throw new IllegalArgumentException("Provide subjectId or attrKey+attrValue");
        }

        if (hasAttrKey) {
            snaps = snapRepository.searchByStringAttr(assinatura.getId(), normalizedSubjectId, attrKey.strip(), attrValue);
        } else {
            snaps = snapRepository.findByAssinaturaIdAndSubjectIdOrderByCreatedAtDesc(assinatura.getId(), normalizedSubjectId);
        }

        List<SnapResponse> items = snaps.stream().map(this::toResponse).toList();
        return new SnapSearchResponse(items.size(), items);
    }

    /**
     * Lists snaps created by the user identified by nickname in the active assinatura.
     *
     * <p>Entrega 2 uses nickname as a temporary identity input before auth/token support. Matching
     * is case-insensitive to reduce accidental fragmentation in local/manual usage.</p>
     */
    public MineSnapsResponse listMineSnaps(String assinaturaCodigo, String assinaturaToken, String nickname) {
        if (!hasText(nickname)) {
            throw new IllegalArgumentException("nickname must be provided");
        }
        AssinaturaEntity assinatura = loadAssinatura(assinaturaCodigo, assinaturaToken);
        String normalizedNickname = nickname.strip();
        List<SnapEntity> snaps = snapRepository.findByAssinaturaIdAndNicknameSnapshotIgnoreCaseOrderByCreatedAtDesc(
                assinatura.getId(),
                normalizedNickname
        );
        List<SnapResponse> items = snaps.stream().map(this::toResponse).toList();
        return new MineSnapsResponse(normalizedNickname, items.size(), items);
    }

    /**
     * Lists videos with snap activity created by the given nickname.
     *
     * <p>The implementation aggregates over the user's snaps in memory because Entrega 2 volumes are
     * still small and this keeps the repository/query layer simple. Items are ordered by most recent
     * snap activity (descending) because the source list is already returned in that order.</p>
     */
    public MineVideosResponse listMineVideos(String assinaturaCodigo, String assinaturaToken, String nickname) {
        if (!hasText(nickname)) {
            throw new IllegalArgumentException("nickname must be provided");
        }
        AssinaturaEntity assinatura = loadAssinatura(assinaturaCodigo, assinaturaToken);
        String normalizedNickname = nickname.strip();
        List<SnapEntity> snaps = snapRepository.findByAssinaturaIdAndNicknameSnapshotIgnoreCaseOrderByCreatedAtDesc(
                assinatura.getId(),
                normalizedNickname
        );

        Map<UUID, MineVideoAccumulator> byVideo = new LinkedHashMap<>();
        for (SnapEntity snap : snaps) {
            // First occurrence is the most recent one because repository returns `createdAt desc`.
            byVideo.computeIfAbsent(
                    snap.getVideoId(),
                    ignored -> new MineVideoAccumulator(snap.getVideoId(), snap.getVideoUrl(), snap.getId(), snap.getCreatedAt())
            ).increment();
        }

        List<MineVideoItemResponse> items = byVideo.values().stream()
                .map(MineVideoAccumulator::toResponse)
                .toList();
        return new MineVideosResponse(normalizedNickname, items.size(), items);
    }

    /**
     * Stores a denormalized projection of subject attributes for indexed queries.
     *
     * <p>Entrega 1 only exposes string-attribute search in the API, but number values are also
     * persisted now so later deliveries can add numeric/range filters without backfill.</p>
     */
    private void persistSubjectAttributes(SnapEntity snap, ProcessingSubjectRequest subject, OffsetDateTime now) {
        if (subject.attributes() == null || subject.attributes().isEmpty()) {
            return;
        }
        List<SnapSubjectAttrEntity> attrs = new ArrayList<>();
        for (ProcessingSubjectAttributeRequest attribute : subject.attributes()) {
            SnapSubjectAttrEntity row = new SnapSubjectAttrEntity();
            row.setSnapId(snap.getId());
            row.setAssinaturaId(snap.getAssinaturaId());
            row.setVideoId(snap.getVideoId());
            row.setSubjectId(subject.id());
            row.setAttrKey(attribute.key().trim().toLowerCase(Locale.ROOT));
            String type = attribute.type().trim().toLowerCase(Locale.ROOT);
            if ("number".equals(type)) {
                row.setValueType("NUMBER");
                row.setNumberValue(BigDecimal.valueOf(attribute.numberValue()));
            } else {
                // Default to string to keep phase-1 tolerant while the template validation is still basic.
                row.setValueType("STRING");
                row.setStringValue(attribute.stringValue());
            }
            row.setCreatedAt(now);
            attrs.add(row);
        }
        snapSubjectAttrRepository.saveAll(attrs);
    }

    /**
     * Resolves the active assinatura from request context, falling back to the configured default.
     *
     * <p>This formalizes tenant context handling for Entrega 3 step 1 while preserving backward
     * compatibility with Entregas 1-2 clients that do not send any tenant header yet.</p>
     */
    private AssinaturaEntity loadAssinatura(String assinaturaCodigo, String assinaturaToken) {
        // When token auth is enabled, allow token-only resolution as a forward-compatible path for
        // external API clients that may not send `X-Assinatura-Codigo`.
        if (snapProperties.isRequireApiToken() && !hasText(assinaturaCodigo) && hasText(assinaturaToken)) {
            String token = assinaturaToken.strip();
            return assinaturaRepository.findByApiToken(token)
                    .orElseThrow(() -> new UnauthorizedException("Invalid assinatura API token"));
        }

        String resolvedCodigo = resolveAssinaturaCodigo(assinaturaCodigo);
        AssinaturaEntity assinatura = assinaturaRepository.findByCodigo(resolvedCodigo)
                .orElseThrow(() -> new NoSuchElementException("Assinatura not found: " + resolvedCodigo));
        validateAssinaturaApiTokenIfEnabled(assinatura, assinaturaToken);
        return assinatura;
    }

    /**
     * Normalizes the incoming assinatura code header and applies default fallback when absent.
     */
    private String resolveAssinaturaCodigo(String assinaturaCodigo) {
        if (!hasText(assinaturaCodigo)) {
            return snapProperties.getDefaultAssinaturaCodigo();
        }
        return assinaturaCodigo.strip();
    }

    /**
     * Feature-flagged token validation for private `/v2/*` routes.
     *
     * <p>When disabled, the method is a no-op. When enabled, the request must provide the token
     * matching the resolved assinatura (`assinatura.api_token`). This keeps the validation path
     * simple and deterministic while auth infrastructure is still being introduced.</p>
     */
    private void validateAssinaturaApiTokenIfEnabled(AssinaturaEntity assinatura, String assinaturaToken) {
        if (!snapProperties.isRequireApiToken()) {
            return;
        }

        if (!hasText(assinaturaToken)) {
            throw new UnauthorizedException("Assinatura API token required");
        }

        String expectedToken = assinatura.getApiToken();
        if (!hasText(expectedToken)) {
            throw new UnauthorizedException("Assinatura API token not configured");
        }

        String providedToken = assinaturaToken.strip();
        if (!Objects.equals(expectedToken, providedToken)) {
            throw new UnauthorizedException("Invalid assinatura API token");
        }
    }

    /**
     * Resolves an existing user by email or creates one, updating nickname snapshots when the
     * user keeps the same email but changes the display nickname.
     */
    private UsuarioEntity resolveUsuario(String nickname, String email, OffsetDateTime now) {
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        Optional<UsuarioEntity> existing = usuarioRepository.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            UsuarioEntity usuario = existing.get();
            String cleanNickname = nickname.strip();
            if (!Objects.equals(usuario.getNickname(), cleanNickname)) {
                usuario.setNickname(cleanNickname);
                usuarioRepository.save(usuario);
            }
            return usuario;
        }
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNickname(nickname.strip());
        usuario.setEmail(normalizedEmail);
        usuario.setStatus("ACTIVE");
        usuario.setCreatedAt(now);
        return usuarioRepository.save(usuario);
    }

    /**
     * Resolves an explicit template inside the active assinatura, or falls back to the default one.
     *
     * <p>Fallback order follows ADR-0004: `is_default=true`, then `slug=default` for compatibility.</p>
     */
    private SubjectTemplateEntity resolveTemplate(Long assinaturaId, Long subjectTemplateId) {
        if (subjectTemplateId != null) {
            return subjectTemplateRepository.findByAssinaturaIdAndId(assinaturaId, subjectTemplateId)
                    .orElseThrow(() -> new NoSuchElementException("subjectTemplateId not found in assinatura ativa: " + subjectTemplateId));
        }
        return subjectTemplateRepository.findByAssinaturaIdAndIsDefaultTrue(assinaturaId)
                .or(() -> subjectTemplateRepository.findByAssinaturaIdAndSlug(assinaturaId, snapProperties.getDefaultTemplateSlug()))
                .orElseThrow(() -> new IllegalStateException("Default subject template not found for assinatura " + assinaturaId));
    }

    /**
     * Reuses an existing video when `videoId` is provided or when a URL canonical hash matches;
     * otherwise creates a new `video` row for the active assinatura.
     */
    private VideoEntity resolveOrCreateVideo(Long assinaturaId, Long usuarioId, String videoIdRaw, String videoUrlRaw, OffsetDateTime now) {
        if (hasText(videoIdRaw)) {
            UUID videoId;
            try {
                videoId = UUID.fromString(videoIdRaw.strip());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("videoId must be a valid UUID");
            }
            return videoRepository.findByIdAndAssinaturaId(videoId, assinaturaId)
                    .orElseThrow(() -> new NoSuchElementException("Video not found: " + videoId));
        }

        String videoUrl = requireVideoUrl(videoUrlRaw);
        String canonicalUrl = canonicalizeUrl(videoUrl);
        String hash = sha256(canonicalUrl);
        return videoRepository.findByAssinaturaIdAndUrlHash(assinaturaId, hash)
                .orElseGet(() -> {
                    VideoEntity video = new VideoEntity();
                    video.setId(UUID.randomUUID());
                    video.setAssinaturaId(assinaturaId);
                    video.setOriginalUrl(videoUrl);
                    video.setCanonicalUrl(canonicalUrl);
                    video.setUrlHash(hash);
                    video.setCreatedByUsuarioId(usuarioId);
                    video.setCreatedAt(now);
                    return videoRepository.save(video);
                });
    }

    /**
     * Builds the subject sent to the MVP processor, applying the `subject.id = snapId` fallback.
     */
    private ProcessingSubjectRequest buildEffectiveSubject(UUID snapId, V2SubjectRequest requestSubject) {
        String subjectId = requestSubject.id();
        if (subjectId == null || subjectId.isBlank()) {
            subjectId = snapId.toString();
        } else {
            subjectId = subjectId.strip();
        }
        return new ProcessingSubjectRequest(subjectId, requestSubject.attributes());
    }

    /**
     * Maps the persistence model to the public `v2` response, hydrating structured JSON payloads.
     */
    private SnapResponse toResponse(SnapEntity snap) {
        return new SnapResponse(
                snap.getId(),
                snap.getVideoId(),
                snap.getStatus(),
                snap.getTipoSnap(),
                snap.getNicknameSnapshot(),
                snap.getEmailSnapshot(),
                snap.getSubjectTemplateId(),
                snap.getDataFilmagem() != null ? snap.getDataFilmagem().toString() : null,
                readJson(snap.getSubjectJson(), V2SubjectRequest.class),
                snap.getVideoUrl(),
                snap.getStartSeconds(),
                snap.getStartFrame(),
                snap.getResolvedStartSeconds(),
                snap.getDurationSeconds(),
                snap.getSnapshotDurationSeconds(),
                snap.getFps(),
                snap.getMaxWidth(),
                snap.getFormat(),
                snap.getQuality(),
                readJsonOrNull(snap.getVideoProbeJson(), ProcessingVideoProbeResponse.class),
                readJsonOrNull(snap.getSnapshotVideoJson(), ProcessingSnapshotVideoResponse.class),
                snap.getFrameCount(),
                readJson(snap.getFramesJson(), FRAMES_TYPE),
                snap.getOutputDir(),
                snap.getErrorMessage(),
                snap.getCreatedAt(),
                snap.getProcessedAt()
        );
    }

    /**
     * Maps a shared snap to the public-safe response contract (Entrega 2).
     */
    private PublicSnapResponse toPublicResponse(SnapEntity snap) {
        return new PublicSnapResponse(
                snap.getId(),
                snap.getPublicShareToken(),
                snap.getStatus(),
                snap.getTipoSnap(),
                snap.getNicknameSnapshot(),
                snap.getDataFilmagem() != null ? snap.getDataFilmagem().toString() : null,
                readJson(snap.getSubjectJson(), V2SubjectRequest.class),
                snap.getResolvedStartSeconds(),
                snap.getDurationSeconds(),
                snap.getSnapshotDurationSeconds(),
                snap.getFrameCount(),
                readJsonOrNull(snap.getVideoProbeJson(), ProcessingVideoProbeResponse.class),
                readJsonOrNull(snap.getSnapshotVideoJson(), ProcessingSnapshotVideoResponse.class),
                readJson(snap.getFramesJson(), FRAMES_TYPE),
                snap.getCreatedAt(),
                snap.getProcessedAt()
        );
    }

    /**
     * Small local helper used throughout the service to keep validation branches readable.
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Validates and normalizes a video URL for phase-1 usage.
     *
     * <p>Only `http`/`https` are accepted because the MVP processing pipeline fetches remotely and
     * Entrega 1 does not yet expose controlled local uploads.</p>
     */
    private static String requireVideoUrl(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            throw new IllegalArgumentException("videoUrl must be provided when videoId is absent");
        }
        String trimmed = videoUrl.strip();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new IllegalArgumentException("videoUrl must use http or https");
        }
        return trimmed;
    }

    /**
     * Placeholder canonicalization function.
     *
     * <p>Entrega 1 only trims the URL. Future deliveries may normalize query params or signed URLs,
     * but changing this requires migration/backfill care because `url_hash` deduplication depends on it.</p>
     */
    private static String canonicalizeUrl(String videoUrl) {
        return videoUrl.strip();
    }

    /**
     * Computes SHA-256 used by `video.url_hash` deduplication.
     */
    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Generates a public share token.
     *
     * <p>A random UUID without dashes is sufficient for Entrega 2. The DB unique index provides the
     * final collision protection should a rare duplicate ever occur.</p>
     */
    private static String generateShareToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Builds the `publicUrl` returned by the share endpoint.
     */
    private String buildPublicUrl(String token) {
        String relativePath = "/public/snaps/" + token;
        if (!hasText(snapProperties.getPublicBaseUrl())) {
            return relativePath;
        }
        String base = snapProperties.getPublicBaseUrl().strip();
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1) + relativePath;
        }
        return base + relativePath;
    }

    /**
     * Normalizes legacy MVP statuses into the public `v2` snap status vocabulary.
     */
    private static String mapSnapStatus(String mvpStatus) {
        return switch (mvpStatus) {
            case "SUCCEEDED", "COMPLETED" -> "COMPLETED";
            case "FAILED" -> "FAILED";
            case "PARTIAL" -> "PARTIAL";
            default -> mvpStatus == null ? "FAILED" : mvpStatus;
        };
    }

    /**
     * Derives snap type for phase-1 UX grouping (`INSTANT` vs `INTERVAL`) from requested durations.
     */
    private static String resolveTipoSnap(CreateSnapRequest request) {
        double duration = request.durationSeconds() == null ? 0.0 : request.durationSeconds();
        double snapshotDuration = effectiveSnapshotDuration(request);
        return Math.max(duration, snapshotDuration) <= 1.0 ? "INSTANT" : "INTERVAL";
    }

    /**
     * Effective snapshot duration mirrors the MVP behavior when the explicit value is omitted.
     */
    private static double effectiveSnapshotDuration(CreateSnapRequest request) {
        return request.snapshotDurationSeconds() != null ? request.snapshotDurationSeconds() : request.durationSeconds();
    }

    /**
     * Applies a default integer when a nullable API field is omitted.
     */
    private static int defaultInt(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    /**
     * Applies the default image format expected by Entrega 1.
     */
    private static String defaultFormat(String format) {
        return format == null ? "jpg" : format.toLowerCase(Locale.ROOT);
    }

    /**
     * Converts the internal MVP subject shape back to the public `v2` DTO shape.
     */
    private V2SubjectRequest toV2Subject(ProcessingSubjectRequest subject) {
        return new V2SubjectRequest(subject.id(), subject.attributes());
    }

    /**
     * Serializes structured payloads stored in JSON columns (`clob` in H2 / text-like in PG).
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JSON", e);
        }
    }

    /**
     * Convenience wrapper for nullable JSON columns.
     */
    private String writeJsonOrNull(Object value) {
        return value == null ? null : writeJson(value);
    }

    /**
     * Nullable variant of JSON deserialization for object payloads.
     */
    private <T> T readJsonOrNull(String json, Class<T> type) {
        return json == null ? null : readJson(json, type);
    }

    /**
     * Reads a persisted JSON object and wraps low-level parsing errors with domain context.
     */
    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read persisted JSON for " + type.getSimpleName(), e);
        }
    }

    /**
     * Reads persisted JSON arrays/lists (used for frame lists).
     */
    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read persisted JSON list", e);
        }
    }

    /**
     * Small mutable accumulator used only for `GET /v2/videos/mine` aggregation.
     */
    private static final class MineVideoAccumulator {
        private final UUID videoId;
        private final String videoUrl;
        private final UUID latestSnapId;
        private final OffsetDateTime latestSnapCreatedAt;
        private int snapCount;

        private MineVideoAccumulator(UUID videoId, String videoUrl, UUID latestSnapId, OffsetDateTime latestSnapCreatedAt) {
            this.videoId = videoId;
            this.videoUrl = videoUrl;
            this.latestSnapId = latestSnapId;
            this.latestSnapCreatedAt = latestSnapCreatedAt;
        }

        private void increment() {
            snapCount++;
        }

        private MineVideoItemResponse toResponse() {
            return new MineVideoItemResponse(videoId, videoUrl, snapCount, latestSnapId, latestSnapCreatedAt);
        }
    }
}
