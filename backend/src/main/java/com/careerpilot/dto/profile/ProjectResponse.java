package com.careerpilot.dto.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {

    private Long id;
    private Long profileId;
    private String title;
    private String description;
    private String role;
    private String technologies;
    private String repoUrl;
    private String liveUrl;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isAcademic;
    private List<String> bullets;
    private Instant createdAt;
    private Instant updatedAt;
}
