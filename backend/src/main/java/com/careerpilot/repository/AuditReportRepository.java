package com.careerpilot.repository;

import com.careerpilot.entity.AuditReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuditReportRepository extends JpaRepository<AuditReport, Long> {

    List<AuditReport> findAllByProfileIdOrderByCreatedAtDesc(Long profileId);

    Page<AuditReport> findAllByProfileIdOrderByCreatedAtDesc(Long profileId, Pageable pageable);

    Optional<AuditReport> findTopByProfileIdOrderByCreatedAtDesc(Long profileId);

    Optional<AuditReport> findByIdAndProfileId(Long id, Long profileId);
}
