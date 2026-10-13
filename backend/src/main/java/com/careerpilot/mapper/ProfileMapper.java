package com.careerpilot.mapper;

import com.careerpilot.dto.profile.ProfileRequest;
import com.careerpilot.dto.profile.ProfileResponse;
import com.careerpilot.entity.Profile;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, builder = @Builder(disableBuilder = true))
public interface ProfileMapper {

    @Mapping(target = "userId", source = "user.id")
    ProfileResponse toResponse(Profile profile);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "profileSkills", ignore = true)
    @Mapping(target = "projects", ignore = true)
    @Mapping(target = "education", ignore = true)
    @Mapping(target = "suggestions", ignore = true)
    @Mapping(target = "auditReports", ignore = true)
    @Mapping(target = "weeklyTasks", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(ProfileRequest dto, @MappingTarget Profile entity);
}
