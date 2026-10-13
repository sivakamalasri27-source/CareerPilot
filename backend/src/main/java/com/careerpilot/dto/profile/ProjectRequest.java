package com.careerpilot.dto.profile;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectRequest {

    @NotBlank(message = "Project title is required and cannot be blank")
    @Size(max = 150, message = "Project title cannot exceed 150 characters")
    private String title;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    @Size(max = 100, message = "Role cannot exceed 100 characters")
    private String role;

    @Size(max = 255, message = "Technologies cannot exceed 255 characters")
    private String technologies;

    @Size(max = 255, message = "Repository URL cannot exceed 255 characters")
    private String repoUrl;

    @Size(max = 255, message = "Live URL cannot exceed 255 characters")
    private String liveUrl;

    private LocalDate startDate;

    private LocalDate endDate;

    @Builder.Default
    private Boolean isAcademic = true;

    @Size(max = 8, message = "Maximum 8 bullets allowed per project")
    @Builder.Default
    private List<@Size(max = 300, message = "Each bullet cannot exceed 300 characters") String> bullets = new ArrayList<>();

    @AssertTrue(message = "End date must not be before start date")
    public boolean isEndDateValid() {
        if (startDate == null || endDate == null) {
            return true;
        }
        return !endDate.isBefore(startDate);
    }

    @AssertTrue(message = "Dates cannot be in the far future")
    public boolean isDatesNotFarFuture() {
        LocalDate maxFuture = LocalDate.now().plusYears(10);
        if (startDate != null && startDate.isAfter(maxFuture)) {
            return false;
        }
        if (endDate != null && endDate.isAfter(maxFuture)) {
            return false;
        }
        return true;
    }
}
