package com.careerpilot.mapper;

import com.careerpilot.dto.profile.EducationRequest;
import com.careerpilot.dto.profile.EducationResponse;
import com.careerpilot.entity.Education;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, builder = @Builder(disableBuilder = true))
public interface EducationMapper {

    @Mapping(target = "profileId", source = "profile.id")
    EducationResponse toResponse(Education education);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "profile", ignore = true)
    Education toEntity(EducationRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "profile", ignore = true)
    void updateEntityFromDto(EducationRequest dto, @MappingTarget Education entity);
}
