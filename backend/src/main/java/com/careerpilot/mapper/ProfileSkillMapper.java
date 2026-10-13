package com.careerpilot.mapper;

import com.careerpilot.dto.profile.ProfileSkillResponse;
import com.careerpilot.entity.ProfileSkill;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProfileSkillMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "skillId", source = "skill.id")
    @Mapping(target = "name", source = "skill.name")
    @Mapping(target = "category", source = "skill.category")
    @Mapping(target = "claimedLevel", source = "claimedLevel")
    @Mapping(target = "verificationStatus", source = "verificationStatus")
    @Mapping(target = "evidenceSummary", source = "evidenceSummary")
    @Mapping(target = "evidenceUrl", source = "evidenceUrl")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    ProfileSkillResponse toResponse(ProfileSkill profileSkill);
}
