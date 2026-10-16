package com.careerpilot.service;

import com.careerpilot.dto.coach.StreakResponse;
import com.careerpilot.dto.coach.WeeklyPlanResponse;
import com.careerpilot.dto.coach.WeeklyTaskResponse;
import com.careerpilot.entity.Profile;
import com.careerpilot.entity.User;

import java.time.LocalDate;
import java.util.List;

public interface WeeklyTaskService {

    WeeklyPlanResponse getOrGenerateWeeklyPlan(User user, Profile profile, boolean regenerate);

    List<WeeklyTaskResponse> getWeeklyTasks(User user, LocalDate weekStart);

    WeeklyTaskResponse completeTask(User user, Long taskId);

    StreakResponse calculateStreak(User user);
}
