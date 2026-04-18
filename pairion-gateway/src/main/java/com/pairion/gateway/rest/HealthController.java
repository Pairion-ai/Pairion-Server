package com.pairion.gateway.rest;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for health and version endpoints.
 *
 * <p>Implements {@code GET /v1/health} and {@code GET /v1/version} as defined in {@code
 * openapi.yaml}.
 */
@RestController
@RequestMapping("/v1")
public class HealthController {

    /**
     * Returns the current health status of the server.
     *
     * @return health response with status "healthy"
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> getHealth() {
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }

    /**
     * Returns version information for the server.
     *
     * @return version response with version, build time, and git commit
     */
    @GetMapping("/version")
    public ResponseEntity<Map<String, String>> getVersion() {
        return ResponseEntity.ok(
                Map.of(
                        "version", "0.1.0",
                        "buildTime", Instant.now().toString(),
                        "gitCommit", "development"));
    }
}
