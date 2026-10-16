package com.careerpilot.service.impl;

import com.careerpilot.dto.role.RoleSkillDto;
import com.careerpilot.dto.role.TargetRoleResponse;
import com.careerpilot.entity.RoleRequiredSkill;
import com.careerpilot.entity.TargetRole;
import com.careerpilot.exception.ResourceNotFoundException;
import com.careerpilot.repository.TargetRoleRepository;
import com.careerpilot.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final TargetRoleRepository targetRoleRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TargetRoleResponse> getAllTargetRoles() {
        return targetRoleRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TargetRole getRoleById(Long roleId) {
        return targetRoleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Target role not found with id: " + roleId));
    }

    private TargetRoleResponse mapToResponse(TargetRole role) {
        List<RoleSkillDto> skills = (role.getRequiredSkills() != null)
                ? role.getRequiredSkills().stream()
                .map(this::mapSkillToDto)
                .toList()
                : Collections.emptyList();

        return TargetRoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .requiredSkillsCount(skills.size())
                .requiredSkills(skills)
                .build();
    }

    private RoleSkillDto mapSkillToDto(RoleRequiredSkill skill) {
        return RoleSkillDto.builder()
                .skillName(skill.getSkillName())
                .category(skill.getCategory())
                .weight(skill.getWeight())
                .priority(skill.getPriority())
                .build();
    }
}
