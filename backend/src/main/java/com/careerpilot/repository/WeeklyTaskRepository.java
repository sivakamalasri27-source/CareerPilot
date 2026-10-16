package com.careerpilot.repository;

import com.careerpilot.entity.WeeklyTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeeklyTaskRepository extends JpaRepository<WeeklyTask, Long> {

    List<WeeklyTask> findAllByUserIdAndWeekStartOrderByCreatedAtAsc(Long userId, LocalDate weekStart);

    List<WeeklyTask> findAllByUserIdOrderByWeekStartDescCreatedAtAsc(Long userId);

    Optional<WeeklyTask> findByIdAndUserId(Long id, Long userId);

    List<WeeklyTask> findAllByUserId(Long userId);

    List<WeeklyTask> findAllByProfileId(Long profileId);
}
