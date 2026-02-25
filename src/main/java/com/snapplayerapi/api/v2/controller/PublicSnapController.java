package com.snapplayerapi.api.v2.controller;

import com.snapplayerapi.api.v2.dto.PublicSnapResponse;
import com.snapplayerapi.api.v2.service.SnapV2Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoints for shared snaps (Entrega 2).
 *
 * <p>This controller is intentionally separated from `/v2` routes because public access does not
 * belong to the authenticated API namespace.</p>
 */
@RestController
@RequestMapping("/public")
public class PublicSnapController {

    private final SnapV2Service snapV2Service;

    public PublicSnapController(SnapV2Service snapV2Service) {
        this.snapV2Service = snapV2Service;
    }

    /**
     * Returns the public representation of a shared snap by share token.
     */
    @GetMapping("/snaps/{token}")
    public ResponseEntity<PublicSnapResponse> getPublicSnap(@PathVariable String token) {
        return ResponseEntity.ok(snapV2Service.getPublicSnap(token));
    }
}
