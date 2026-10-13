package com.careerpilot.repository;

import com.careerpilot.entity.WeeklyTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WeeklyTaskRepository extends JpaRepository<WeeklyTask, Long> {

    List<WeeklyTask> findAllByProfileId(Long profileId);

    List<WeeklyTask> findAllByProfileIdOrderByDueDateAsc(Long profileId);

    List<WeeklyTask> findAllByProfileIdAndIsCompletedFalse(Long profileId);
}
