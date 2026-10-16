package com.careerpilot.service;

import com.careerpilot.dto.resume.ResumeUploadResponse;
import com.careerpilot.dto.resume.ResumeUploadSummaryResponse;
import com.careerpilot.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ResumeService {

    ResumeUploadResponse uploadAndProcessResume(User user, MultipartFile file);

    List<ResumeUploadSummaryResponse> getResumeUploadHistory(User user);

    void deleteResumeUpload(User user, Long resumeId);
}
