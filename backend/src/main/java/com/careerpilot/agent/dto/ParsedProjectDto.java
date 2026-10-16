package com.careerpilot.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedProjectDto {

    private String title;
    private String description;
    private String technologies;
    private String role;

    @Builder.Default
    private List<String> bullets = new ArrayList<>();
}
