package com.careerpilot.dto.profile;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationRequest {

    @NotBlank(message = "Institution is required and cannot be blank")
    @Size(max = 255, message = "Institution cannot exceed 255 characters")
    private String institution;

    @NotBlank(message = "Degree is required and cannot be blank")
    @Size(max = 255, message = "Degree cannot exceed 255 characters")
    private String degree;

    @Size(max = 255, message = "Field of study cannot exceed 255 characters")
    private String fieldOfStudy;

    private LocalDate startDate;

    private LocalDate endDate;

    @Size(max = 50, message = "Grade/GPA cannot exceed 50 characters")
    private String gradeOrGpa;

    @Size(max = 2000, message = "Activities cannot exceed 2000 characters")
    private String activities;

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
