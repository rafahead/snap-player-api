package com.snapplayerapi.api.v2.controller;

import com.snapplayerapi.api.v2.dto.CreateSnapRequest;
import com.snapplayerapi.api.v2.dto.MineSnapsResponse;
import com.snapplayerapi.api.v2.dto.MineVideosResponse;
import com.snapplayerapi.api.v2.dto.ShareSnapResponse;
import com.snapplayerapi.api.v2.dto.SnapResponse;
import com.snapplayerapi.api.v2.dto.SnapSearchResponse;
import com.snapplayerapi.api.v2.dto.VideoSnapsResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.snapplayerapi.api.v2.config.SnapProperties;
import com.snapplayerapi.api.v2.service.SnapV2Service;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints da Entrega 1 para a API Snap-first síncrona.
 *
 * <p>Este controller expõe a camada HTTP da v2, mantendo a lógica de negócio no serviço
 * {@link SnapV2Service}. A ideia é estabilizar o contrato REST agora e evoluir a implementação
 * depois (assíncrono, auth, multi-assinatura real) sem quebrar clientes.</p>
 */
@RestController
@RequestMapping("/v2")
@Validated
public class SnapV2Controller {

    /**
     * Optional header used to formalize tenant context before full auth/token support is added.
     *
     * <p>When omitted, the service still falls back to the configured default assinatura (`default`)
     * to preserve backward compatibility with Entregas 1-2 clients.</p>
     */
    public static final String ASSINATURA_HEADER = "X-Assinatura-Codigo";
    /**
     * Optional API token header for private routes.
     *
     * <p>The validation is feature-flagged in Entrega 3 step 2, so the header may be ignored when
     * `app.snap.requireApiToken=false`.</p>
     */
    public static final String ASSINATURA_TOKEN_HEADER = "X-Assinatura-Token";

    private final SnapV2Service snapV2Service;
    private final SnapProperties snapProperties;

    public SnapV2Controller(SnapV2Service snapV2Service, SnapProperties snapProperties) {
        this.snapV2Service = snapV2Service;
        this.snapProperties = snapProperties;
    }

    /**
     * Cria um snap síncrono.
     *
     * <p>Na Entrega 1 este endpoint reaproveita o pipeline do MVP (ffprobe/ffmpeg local)
     * e persiste o resultado final em banco local.</p>
     */
    @PostMapping("/snaps")
    public ResponseEntity<SnapResponse> createSnap(
            @RequestHeader(name = ASSINATURA_HEADER, required = false) String assinaturaCodigo,
            @RequestHeader(name = ASSINATURA_TOKEN_HEADER, required = false) String assinaturaToken,
            @RequestBody @Valid CreateSnapRequest request
    ) {
        SnapResponse response = snapV2Service.createSnap(assinaturaCodigo, assinaturaToken, request);
        HttpStatus status = snapProperties.isAsyncCreateEnabled() ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Retorna o detalhe completo de um snap já persistido.
     */
    @GetMapping("/snaps/{snapId}")
    public ResponseEntity<SnapResponse> getSnap(
            @RequestHeader(name = ASSINATURA_HEADER, required = false) String assinaturaCodigo,
            @RequestHeader(name = ASSINATURA_TOKEN_HEADER, required = false) String assinaturaToken,
            @PathVariable UUID snapId
    ) {
        return ResponseEntity.ok(snapV2Service.getSnap(assinaturaCodigo, assinaturaToken, snapId));
    }

    /**
     * Generates (or returns) a public share token for a snap.
     *
     * <p>Entrega 2 behavior is idempotent so clients can safely call this endpoint multiple times
     * when preparing a share UI.</p>
     */
    @PostMapping("/snaps/{snapId}/share")
    public ResponseEntity<ShareSnapResponse> shareSnap(
            @RequestHeader(name = ASSINATURA_HEADER, required = false) String assinaturaCodigo,
            @RequestHeader(name = ASSINATURA_TOKEN_HEADER, required = false) String assinaturaToken,
            @PathVariable UUID snapId
    ) {
        return ResponseEntity.ok(snapV2Service.shareSnap(assinaturaCodigo, assinaturaToken, snapId));
    }

    /**
     * Lista snaps de um vídeo no contexto da assinatura ativa (Entrega 1: `default`).
     *
     * <p>Permite filtro opcional por nickname para suportar listas "mine" básicas
     * antes da autenticação completa. Entrega 3 padroniza paginação/ordenação (`offset`, `limit`,
     * `sortBy`, `sortDir`) para manter consistência com as demais listas.</p>
     */
    @GetMapping("/videos/{videoId}/snaps")
    public ResponseEntity<VideoSnapsResponse> listByVideo(
            @RequestHeader(name = ASSINATURA_HEADER, required = false) String assinaturaCodigo,
            @RequestHeader(name = ASSINATURA_TOKEN_HEADER, required = false) String assinaturaToken,
            @PathVariable UUID videoId,
            @RequestParam(required = false) String nickname,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir
    ) {
        return ResponseEntity.ok(snapV2Service.listSnapsByVideo(
                assinaturaCodigo, assinaturaToken, videoId, nickname, offset, limit, sortBy, sortDir
        ));
    }

    /**
     * Busca snaps por `subjectId` e/ou atributo string.
     *
     * <p>Escopo intencionalmente limitado na Entrega 1: apenas igualdade string
     * (`attrKey` + `attrValue`) e/ou filtro por `subjectId`. Entrega 3 adiciona paginação/ordenação
     * padronizadas para evitar contratos diferentes entre endpoints de lista.</p>
     */
    @GetMapping("/snaps/search")
    public ResponseEntity<SnapSearchResponse> search(
            @RequestHeader(name = ASSINATURA_HEADER, required = false) String assinaturaCodigo,
            @RequestHeader(name = ASSINATURA_TOKEN_HEADER, required = false) String assinaturaToken,
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false) String attrKey,
            @RequestParam(required = false) String attrValue,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir
    ) {
        return ResponseEntity.ok(snapV2Service.search(
                assinaturaCodigo, assinaturaToken, subjectId, attrKey, attrValue, offset, limit, sortBy, sortDir
        ));
    }

    /**
     * Lists snaps created by the user identified by `nickname` in the active assinatura.
     *
     * <p>This is a temporary identity mechanism used until auth/token support is introduced in a
     * later delivery. Entrega 3 standardizes paging/sorting params to match other list routes.</p>
     */
    @GetMapping("/snaps/mine")
    public ResponseEntity<MineSnapsResponse> listMySnaps(
            @RequestHeader(name = ASSINATURA_HEADER, required = false) String assinaturaCodigo,
            @RequestHeader(name = ASSINATURA_TOKEN_HEADER, required = false) String assinaturaToken,
            @RequestParam @NotBlank String nickname,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir
    ) {
        return ResponseEntity.ok(snapV2Service.listMineSnaps(
                assinaturaCodigo, assinaturaToken, nickname, offset, limit, sortBy, sortDir
        ));
    }

    /**
     * Lists videos with activity (snaps) created by the user identified by `nickname`.
     */
    @GetMapping("/videos/mine")
    public ResponseEntity<MineVideosResponse> listMyVideos(
            @RequestHeader(name = ASSINATURA_HEADER, required = false) String assinaturaCodigo,
            @RequestHeader(name = ASSINATURA_TOKEN_HEADER, required = false) String assinaturaToken,
            @RequestParam @NotBlank String nickname,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir
    ) {
        return ResponseEntity.ok(snapV2Service.listMineVideos(
                assinaturaCodigo, assinaturaToken, nickname, offset, limit, sortBy, sortDir
        ));
    }
}
