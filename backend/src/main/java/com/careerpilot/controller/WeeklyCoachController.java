package com.careerpilot.controller;

import com.careerpilot.dto.coach.StreakResponse;
import com.careerpilot.dto.coach.WeeklyPlanResponse;
import com.careerpilot.dto.coach.WeeklyTaskResponse;
import com.careerpilot.entity.Profile;
import com.careerpilot.entity.User;
import com.careerpilot.exception.ResourceNotFoundException;
import com.careerpilot.repository.ProfileRepository;
import com.careerpilot.repository.UserRepository;
import com.careerpilot.service.WeeklyTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/coach")
@RequiredArgsConstructor
@Tag(name = "Weekly Coach", description = "Endpoints for generating weekly action plans, completing tasks, and tracking consistency streaks")
@SecurityRequirement(name = "bearerAuth")
public class WeeklyCoachController {

    private final WeeklyTaskService weeklyTaskService;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    @GetMapping("/weekly-tasks")
    @Operation(summary = "Get current weekly tasks",
            description = "Retrieves tasks for the specified week (or current ISO week by default) for the authenticated user.")
    public ResponseEntity<List<WeeklyTaskResponse>> getWeeklyTasks(
            @RequestParam(value = "weekStart", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = resolveAuthenticatedUser(userDetails);
        List<WeeklyTaskResponse> tasks = weeklyTaskService.getWeeklyTasks(user, weekStart);
        return ResponseEntity.ok(tasks);
    }

    @PostMapping("/weekly-tasks/generate")
    @Operation(summary = "Generate or retrieve weekly plan",
            description = "Generates exactly 3 concrete tasks based on latest audit and skill gap. Idempotent: returns existing plan for current week unless regenerate=true.")
    public ResponseEntity<WeeklyPlanResponse> generateWeeklyPlan(
            @RequestParam(value = "regenerate", defaultValue = "false") boolean regenerate,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = resolveAuthenticatedUser(userDetails);
        Profile profile = resolveUserProfile(user);

        WeeklyPlanResponse plan = weeklyTaskService.getOrGenerateWeeklyPlan(user, profile, regenerate);
        return ResponseEntity.ok(plan);
    }

    @PatchMapping("/weekly-tasks/{id}/complete")
    @Operation(summary = "Mark weekly task as complete",
            description = "Marks a task as DONE. Strictly restricted to task owner; returns 404 if not found or belongs to another user.")
    public ResponseEntity<WeeklyTaskResponse> completeTask(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = resolveAuthenticatedUser(userDetails);
        WeeklyTaskResponse response = weeklyTaskService.completeTask(user, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/streak")
    @Operation(summary = "Get user weekly consistency streak",
            description = "Calculates consecutive weeks where at least 2 of 3 tasks were completed.")
    public ResponseEntity<StreakResponse> getStreak(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = resolveAuthenticatedUser(userDetails);
        StreakResponse streak = weeklyTaskService.calculateStreak(user);
        return ResponseEntity.ok(streak);
    }

    private User resolveAuthenticatedUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new ResourceNotFoundException("User not authenticated");
        }
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userDetails.getUsername()));
    }

    private Profile resolveUserProfile(User user) {
        return profileRepository.findByUserId(user.getId())
                .orElseGet(() -> profileRepository.save(Profile.builder().user(user).build()));
    }
}
