package com.careerpilot.dto.coach;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreakResponse {
    private int currentStreakWeeks;
    private int longestStreakWeeks;
    private LocalDate currentWeekStart;
    private int currentWeekCompletedTasks;
    private int currentWeekTotalTasks;
    private boolean currentWeekGoalMet;
}
