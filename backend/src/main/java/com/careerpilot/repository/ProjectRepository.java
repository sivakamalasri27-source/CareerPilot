package com.careerpilot.repository;

import com.careerpilot.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByProfileId(Long profileId);

    List<Project> findAllByProfileIdOrderByStartDateDesc(Long profileId);
}
