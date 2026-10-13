package com.careerpilot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(exclude = "profile")
public class Project extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "role", length = 100)
    private String role;

    @Column(name = "technologies", length = 255)
    private String technologies;

    @Column(name = "repo_url", length = 255)
    private String repoUrl;

    @Column(name = "live_url", length = 255)
    private String liveUrl;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_academic", nullable = false)
    @Builder.Default
    private Boolean isAcademic = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bullets", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> bullets = new ArrayList<>();
}
