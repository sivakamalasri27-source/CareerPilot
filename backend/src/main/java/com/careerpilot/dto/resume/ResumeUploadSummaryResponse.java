package com.careerpilot.dto.resume;

import com.careerpilot.entity.UploadStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeUploadSummaryResponse {

    private Long id;
    private String fileName;
    private Long fileSizeBytes;
    private UploadStatus status;
    private Instant createdAt;
}
