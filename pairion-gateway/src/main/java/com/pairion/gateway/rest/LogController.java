package com.pairion.gateway.rest;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for client log forwarding.
 *
 * <p>Accepts client log records via {@code POST /v1/logs} and forwards them through the server's
 * Logback pipeline as defined in {@code openapi.yaml}.
 */
@RestController
@RequestMapping("/v1/logs")
public class LogController {

    private static final Logger log = LoggerFactory.getLogger(LogController.class);

    /**
     * Accepts and forwards client log records.
     *
     * @param records the list of client log records
     * @return 204 No Content
     */
    @PostMapping
    public ResponseEntity<Void> postLogs(@RequestBody List<Map<String, Object>> records) {
        for (Map<String, Object> record : records) {
            log.info(
                    "Client log: level={}, message={}",
                    record.getOrDefault("level", "UNKNOWN"),
                    record.getOrDefault("message", ""));
        }
        return ResponseEntity.noContent().build();
    }
}
