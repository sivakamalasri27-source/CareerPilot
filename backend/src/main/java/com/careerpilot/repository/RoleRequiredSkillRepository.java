package com.careerpilot.repository;

import com.careerpilot.entity.RoleRequiredSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoleRequiredSkillRepository extends JpaRepository<RoleRequiredSkill, Long> {

    List<RoleRequiredSkill> findAllByRoleId(Long roleId);
}
