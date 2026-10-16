package com.careerpilot.controller;

import com.careerpilot.dto.role.TargetRoleResponse;
import com.careerpilot.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Target Roles", description = "Endpoints for exploring curated target career roles and required skill stacks")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @Operation(summary = "List curated target career roles",
            description = "Returns all curated industry roles with weighted skill requirements, priorities, and descriptions.")
    public ResponseEntity<List<TargetRoleResponse>> listTargetRoles() {
        return ResponseEntity.ok(roleService.getAllTargetRoles());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get target career role details",
            description = "Returns a single target role by ID with its complete required skill stack.")
    public ResponseEntity<TargetRoleResponse> getTargetRole(@PathVariable Long id) {
        return ResponseEntity.ok(
                TargetRoleResponse.builder()
                        .id(roleService.getRoleById(id).getId())
                        .name(roleService.getRoleById(id).getName())
                        .description(roleService.getRoleById(id).getDescription())
                        .requiredSkillsCount(roleService.getRoleById(id).getRequiredSkills() != null ? roleService.getRoleById(id).getRequiredSkills().size() : 0)
                        .build()
        );
    }
}
