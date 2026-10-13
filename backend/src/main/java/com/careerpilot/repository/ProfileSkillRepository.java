package com.careerpilot.repository;

import com.careerpilot.entity.ProfileSkill;
import com.careerpilot.entity.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileSkillRepository extends JpaRepository<ProfileSkill, Long> {

    List<ProfileSkill> findAllByProfileId(Long profileId);

    Optional<ProfileSkill> findByProfileIdAndSkillId(Long profileId, Long skillId);

    List<ProfileSkill> findAllByProfileIdAndVerificationStatus(Long profileId, VerificationStatus status);

    boolean existsByProfileIdAndSkillId(Long profileId, Long skillId);
}
