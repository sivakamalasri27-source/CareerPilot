package com.careerpilot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "job_match_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"user", "profile"})
@EntityListeners(AuditingEntityListener.class)
public class JobMatchReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "job_description", nullable = false, columnDefinition = "TEXT")
    private String jobDescription;

    @Column(name = "match_percentage", nullable = false)
    private Double matchPercentage;

    @Column(name = "extracted_experience")
    private String extractedExperience;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_skills", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> matchedSkills = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_skills", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> missingSkills = new ArrayList<>();

    @Column(name = "fit_summary", columnDefinition = "TEXT")
    private String fitSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_tweaks", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> profileTweaks = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interview_topics", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> interviewTopics = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
