package com.careerpilot.dto;

import com.careerpilot.dto.coach.StreakResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StreakResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("Should serialize both current and alias property names for dashboard sync")
    void shouldSerializeAliasesForDashboard() throws Exception {
        StreakResponse response = StreakResponse.builder()
                .currentStreakWeeks(3)
                .longestStreakWeeks(5)
                .currentWeekStart(LocalDate.of(2026, 10, 19))
                .currentWeekCompletedTasks(2)
                .currentWeekTotalTasks(3)
                .currentWeekGoalMet(true)
                .build();

        String json = objectMapper.writeValueAsString(response);
        Map<?, ?> map = objectMapper.readValue(json, Map.class);

        assertThat(map.get("currentStreakWeeks")).isEqualTo(3);
        assertThat(map.get("activeStreakWeeks")).isEqualTo(3);
        assertThat(map.get("currentWeekCompletedTasks")).isEqualTo(2);
        assertThat(map.get("completedTasksThisWeek")).isEqualTo(2);
        assertThat(map.get("currentWeekTotalTasks")).isEqualTo(3);
        assertThat(map.get("totalTasksThisWeek")).isEqualTo(3);
        assertThat(map.get("currentWeekGoalMet")).isEqualTo(true);
        assertThat(map.get("currentWeekMetGoal")).isEqualTo(true);
    }
}
