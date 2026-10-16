package com.careerpilot.tool.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubRepoDto {

    private String name;

    private String description;

    private String language;

    @Builder.Default
    private List<String> topics = new ArrayList<>();

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("fork")
    @Builder.Default
    private Boolean fork = false;

    @JsonProperty("pushed_at")
    private String pushedAt;
}
