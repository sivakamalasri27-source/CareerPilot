package com.careerpilot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "weekly_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = {"user", "profile"})
public class WeeklyTask extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id")
    private Profile profile;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "estimated_time", length = 50)
    private String estimatedTime;

    @Column(name = "why_this_matters", columnDefinition = "TEXT")
    private String whyThisMatters;

    @Column(name = "verification_type", length = 50)
    @Builder.Default
    private String verificationType = "SELF_REPORTED";

    @Column(name = "verification_hint", length = 255)
    private String verificationHint;

    @Column(name = "verification_status", length = 50)
    @Builder.Default
    private String verificationStatus = "UNVERIFIED";

    @Column(name = "proof_url", length = 500)
    private String proofUrl;

    @Column(name = "baseline_count")
    @Builder.Default
    private Integer baselineCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "streak_count", nullable = false)
    @Builder.Default
    private Integer streakCount = 0;
}
