package com.pairion.gateway.rest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for household and user management endpoints.
 *
 * <p>Implements household CRUD operations as defined in {@code openapi.yaml}. All responses are
 * stubs in M0.
 */
@RestController
@RequestMapping("/v1/household")
public class HouseholdController {

    /**
     * Returns the household summary.
     *
     * @return stub household response
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getHousehold() {
        return ResponseEntity.ok(
                Map.of(
                        "id", UUID.randomUUID().toString(),
                        "createdAt", Instant.now().toString(),
                        "users", List.of()));
    }

    /**
     * Lists all users in the household.
     *
     * @return empty list of users (stub)
     */
    @GetMapping("/users")
    public ResponseEntity<List<Object>> listUsers() {
        return ResponseEntity.ok(List.of());
    }

    /**
     * Creates a new user in the household.
     *
     * @param body the create user request containing role and displayName
     * @return the created user with a generated ID
     */
    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, Object> body) {
        Map<String, Object> user =
                Map.of(
                        "id", UUID.randomUUID().toString(),
                        "role", body.getOrDefault("role", "MEMBER"),
                        "displayName", body.getOrDefault("displayName", "New User"),
                        "createdAt", Instant.now().toString(),
                        "enrollmentComplete", false);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    /**
     * Returns details for a specific user.
     *
     * @param userId the user ID
     * @return stub user response
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable String userId) {
        return ResponseEntity.ok(
                Map.of(
                        "id",
                        userId,
                        "role",
                        "MEMBER",
                        "displayName",
                        "Stub User",
                        "createdAt",
                        Instant.now().toString(),
                        "enrollmentComplete",
                        false));
    }

    /**
     * Removes a user from the household.
     *
     * @param userId the user ID to remove
     * @return 204 No Content
     */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        return ResponseEntity.noContent().build();
    }
}
