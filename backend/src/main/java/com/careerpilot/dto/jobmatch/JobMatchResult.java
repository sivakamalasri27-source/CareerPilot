package com.careerpilot.dto.jobmatch;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobMatchResult {
    private String fitSummary;
    @Builder.Default
    private List<String> profileTweaks = new ArrayList<>();
    @Builder.Default
    private List<String> interviewTopics = new ArrayList<>();
}
