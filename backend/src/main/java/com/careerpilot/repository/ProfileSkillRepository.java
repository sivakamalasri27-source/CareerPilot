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

    org.springframework.data.domain.Page<ProfileSkill> findAllByProfileId(Long profileId, org.springframework.data.domain.Pageable pageable);

    Optional<ProfileSkill> findByProfileIdAndSkillId(Long profileId, Long skillId);

    Optional<ProfileSkill> findByIdAndProfileId(Long id, Long profileId);

    List<ProfileSkill> findAllByProfileIdAndVerificationStatus(Long profileId, VerificationStatus status);

    boolean existsByProfileIdAndSkillId(Long profileId, Long skillId);

    long countByProfileId(Long profileId);

    @org.springframework.data.jpa.repository.Query("SELECT CASE WHEN COUNT(ps) > 0 THEN true ELSE false END FROM ProfileSkill ps WHERE ps.profile.id = :profileId AND LOWER(ps.skill.name) = LOWER(:skillName)")
    boolean existsByProfileIdAndSkillNameIgnoreCase(@org.springframework.data.repository.query.Param("profileId") Long profileId, @org.springframework.data.repository.query.Param("skillName") String skillName);
}
