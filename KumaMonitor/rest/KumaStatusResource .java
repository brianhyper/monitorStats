package com.hyperbrains.platform.web.rest;

import com.hyperbrains.platform.service.kuma.KumaSnapshot;
import com.hyperbrains.platform.service.kuma.KumaSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only view of Kuma's monitors. */
@RestController
@RequestMapping("/api/kuma")
public class KumaStatusResource {

    private final KumaSource source;

    public KumaStatusResource(KumaSource source) {
        this.source = source;
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN')")
    public ResponseEntity<KumaSnapshot> status() {
        return ResponseEntity.ok(source.current());
    }
}