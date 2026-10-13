package com.careerpilot.repository;

import com.careerpilot.entity.Education;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EducationRepository extends JpaRepository<Education, Long> {

    List<Education> findAllByProfileId(Long profileId);

    Page<Education> findAllByProfileId(Long profileId, Pageable pageable);

    List<Education> findAllByProfileIdOrderByStartDateDesc(Long profileId);

    Optional<Education> findByIdAndProfileId(Long id, Long profileId);
}
