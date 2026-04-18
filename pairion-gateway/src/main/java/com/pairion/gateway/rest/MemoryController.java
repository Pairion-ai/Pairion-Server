package com.pairion.gateway.rest;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for memory browsing endpoints.
 *
 * <p>Implements episode listing as defined in {@code openapi.yaml}. All responses are stubs in M0.
 */
@RestController
@RequestMapping("/v1/memory")
public class MemoryController {

    /**
     * Lists episodes for a given user.
     *
     * @param userId the user whose episodes to list
     * @param limit maximum number of episodes to return
     * @return empty list of episodes (stub)
     */
    @GetMapping("/episodes")
    public ResponseEntity<List<Object>> listEpisodes(
            @RequestParam String userId, @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(List.of());
    }
}
