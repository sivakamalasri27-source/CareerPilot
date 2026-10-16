package com.careerpilot.service;

import com.careerpilot.dto.role.TargetRoleResponse;
import com.careerpilot.entity.TargetRole;

import java.util.List;

public interface RoleService {

    List<TargetRoleResponse> getAllTargetRoles();

    TargetRole getRoleById(Long roleId);
}
