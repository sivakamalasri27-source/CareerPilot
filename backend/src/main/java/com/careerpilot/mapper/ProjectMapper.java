package com.careerpilot.mapper;

import com.careerpilot.dto.profile.ProjectRequest;
import com.careerpilot.dto.profile.ProjectResponse;
import com.careerpilot.entity.Project;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, builder = @Builder(disableBuilder = true))
public interface ProjectMapper {

    @Mapping(target = "profileId", source = "profile.id")
    ProjectResponse toResponse(Project project);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "profile", ignore = true)
    Project toEntity(ProjectRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "profile", ignore = true)
    void updateEntityFromDto(ProjectRequest dto, @MappingTarget Project entity);
}
