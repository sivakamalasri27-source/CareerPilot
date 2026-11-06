package com.careerpilot.dto.coach;

import com.careerpilot.entity.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyTaskResponse {
    private Long id;
    private String title;
    private String description;
    private String category;
    private String estimatedTime;
    private String whyThisMatters;
    private String verificationType;
    private String verificationHint;
    private String verificationStatus;
    private String proofUrl;
    private TaskStatus status;
    private LocalDate weekStart;
    private Instant completedAt;
    private Instant createdAt;
}
