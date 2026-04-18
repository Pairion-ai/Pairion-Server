package com.pairion.gateway.rest;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for skill catalog endpoints.
 *
 * <p>Implements skill listing and detail retrieval as defined in {@code openapi.yaml}. All
 * responses are stubs in M0.
 */
@RestController
@RequestMapping("/v1/skills")
public class SkillController {

    /**
     * Lists all installed skills.
     *
     * @return empty list of skills (stub)
     */
    @GetMapping
    public ResponseEntity<List<Object>> listSkills() {
        return ResponseEntity.ok(List.of());
    }

    /**
     * Returns details for a specific skill.
     *
     * @param skillId the skill ID
     * @return stub skill response
     */
    @GetMapping("/{skillId}")
    public ResponseEntity<Map<String, Object>> getSkill(@PathVariable String skillId) {
        return ResponseEntity.ok(
                Map.of(
                        "id",
                        skillId,
                        "name",
                        "Stub Skill",
                        "description",
                        "Placeholder skill for M0 walking skeleton",
                        "permittedUsers",
                        List.of()));
    }
}
