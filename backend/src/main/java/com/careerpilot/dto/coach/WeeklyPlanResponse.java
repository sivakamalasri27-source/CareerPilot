package com.careerpilot.dto.coach;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyPlanResponse {
    private LocalDate weekStart;
    private List<WeeklyTaskResponse> tasks;
    private boolean isNewPlan;
    private String focusArea;
    private String message;
}
