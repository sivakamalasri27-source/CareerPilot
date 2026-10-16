package com.careerpilot.repository;

import com.careerpilot.entity.SkillGapReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillGapReportRepository extends JpaRepository<SkillGapReport, Long> {

    Optional<SkillGapReport> findTopByProfileIdOrderByCreatedAtDesc(Long profileId);

    List<SkillGapReport> findAllByProfileIdOrderByCreatedAtDesc(Long profileId);
}
