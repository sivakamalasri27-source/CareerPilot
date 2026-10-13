package com.careerpilot.repository;

import com.careerpilot.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByProfileId(Long profileId);

    Page<Project> findAllByProfileId(Long profileId, Pageable pageable);

    List<Project> findAllByProfileIdOrderByStartDateDesc(Long profileId);

    Optional<Project> findByIdAndProfileId(Long id, Long profileId);
}
