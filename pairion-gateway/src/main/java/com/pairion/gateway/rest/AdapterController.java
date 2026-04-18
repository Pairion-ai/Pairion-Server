package com.pairion.gateway.rest;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for adapter configuration endpoints.
 *
 * <p>Implements adapter listing and detail retrieval as defined in {@code openapi.yaml}. All
 * responses are stubs in M0.
 */
@RestController
@RequestMapping("/v1/adapters")
public class AdapterController {

    /**
     * Lists all configured adapters.
     *
     * @return empty list of adapters (stub)
     */
    @GetMapping
    public ResponseEntity<List<Object>> listAdapters() {
        return ResponseEntity.ok(List.of());
    }

    /**
     * Returns details for a specific adapter by category and name.
     *
     * @param category the adapter category (llm, tts, stt, etc.)
     * @param name the adapter name
     * @return stub adapter response
     */
    @GetMapping("/{category}/{name}")
    public ResponseEntity<Map<String, Object>> getAdapter(
            @PathVariable String category, @PathVariable String name) {
        return ResponseEntity.ok(
                Map.of(
                        "category",
                        category,
                        "name",
                        name,
                        "enabled",
                        false,
                        "capabilities",
                        Map.of()));
    }
}
