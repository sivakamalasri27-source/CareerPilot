package com.careerpilot.agent.util;

import com.careerpilot.entity.Education;
import com.careerpilot.entity.Profile;
import com.careerpilot.entity.ProfileSkill;
import com.careerpilot.entity.Project;

import java.util.List;

public final class ProfileFormatter {

    private ProfileFormatter() {
    }

    /**
     * Formats a Profile entity and its associated records into a structured text representation for the AI prompts.
     */
    public static String formatProfileForAi(Profile profile) {
        if (profile == null) {
            return "No profile data provided.";
        }

        StringBuilder sb = new StringBuilder();

        sb.append("--- CANDIDATE PROFILE ---\n");
        if (profile.getUser() != null && profile.getUser().getFullName() != null) {
            sb.append("Full Name: ").append(profile.getUser().getFullName()).append("\n");
        }
        sb.append("Headline: ").append(profile.getHeadline() != null ? profile.getHeadline() : "Not specified").append("\n");
        sb.append("Target Role: ").append(profile.getTargetRole() != null ? profile.getTargetRole() : "Not specified").append("\n");
        sb.append("About/Summary: ").append(profile.getAbout() != null ? profile.getAbout() : "None provided").append("\n\n");

        // Skills
        sb.append("--- SKILLS ---\n");
        List<ProfileSkill> skills = profile.getProfileSkills();
        if (skills == null || skills.isEmpty()) {
            sb.append("No skills listed.\n\n");
        } else {
            for (ProfileSkill ps : skills) {
                String skillName = ps.getSkill() != null ? ps.getSkill().getName() : "Unknown";
                sb.append("- ").append(skillName)
                        .append(" (").append(ps.getClaimedLevel() != null ? ps.getClaimedLevel() : "BEGINNER").append(")\n");
            }
            sb.append("\n");
        }

        // Projects
        sb.append("--- PROJECTS ---\n");
        List<Project> projects = profile.getProjects();
        if (projects == null || projects.isEmpty()) {
            sb.append("No projects listed.\n\n");
        } else {
            for (Project p : projects) {
                sb.append("Project: ").append(p.getTitle()).append("\n");
                if (p.getDescription() != null && !p.getDescription().isBlank()) {
                    sb.append("Description: ").append(p.getDescription()).append("\n");
                }
                if (p.getTechnologies() != null && !p.getTechnologies().isBlank()) {
                    sb.append("Technologies: ").append(p.getTechnologies()).append("\n");
                }
                if (p.getRepoUrl() != null && !p.getRepoUrl().isBlank()) {
                    sb.append("GitHub/Repo: ").append(p.getRepoUrl()).append("\n");
                }
                if (p.getLiveUrl() != null && !p.getLiveUrl().isBlank()) {
                    sb.append("Live URL: ").append(p.getLiveUrl()).append("\n");
                }
                if (p.getBullets() != null && !p.getBullets().isEmpty()) {
                    sb.append("Bullet Points:\n");
                    for (String bullet : p.getBullets()) {
                        sb.append("  * ").append(bullet).append("\n");
                    }
                }
                sb.append("\n");
            }
        }

        // Education
        sb.append("--- EDUCATION ---\n");
        List<Education> educations = profile.getEducation();
        if (educations == null || educations.isEmpty()) {
            sb.append("No education listed.\n");
        } else {
            for (Education e : educations) {
                sb.append("Institution: ").append(e.getInstitution()).append("\n");
                sb.append("Degree: ").append(e.getDegree()).append(" in ").append(e.getFieldOfStudy()).append("\n");
                if (e.getGradeOrGpa() != null) {
                    sb.append("GPA/Grade: ").append(e.getGradeOrGpa()).append("\n");
                }
                sb.append("\n");
            }
        }

        return sb.toString().trim();
    }

    /**
     * Validates that the profile is not empty or near-empty before calling AI agents.
     * Throws IllegalArgumentException if profile is deemed empty/near-empty.
     */
    public static void validateNotEmpty(Profile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("Profile is incomplete or empty. Please create your profile before requesting an audit.");
        }

        boolean hasHeadline = profile.getHeadline() != null && !profile.getHeadline().isBlank();
        boolean hasAbout = profile.getAbout() != null && !profile.getAbout().isBlank();
        boolean hasSkills = profile.getProfileSkills() != null && !profile.getProfileSkills().isEmpty();
        boolean hasProjects = profile.getProjects() != null && !profile.getProjects().isEmpty();
        boolean hasEducation = profile.getEducation() != null && !profile.getEducation().isEmpty();

        // Reject if headline is missing or if there are no skills, projects, and education
        if (!hasHeadline || (!hasSkills && !hasProjects && !hasEducation)) {
            throw new IllegalArgumentException(
                    "Profile is incomplete or empty. Please add a headline and at least one skill, project, or education entry before requesting an audit.");
        }
    }
}
