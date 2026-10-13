package com.careerpilot.repository;

import com.careerpilot.entity.ResumeUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeUploadRepository extends JpaRepository<ResumeUpload, Long> {

    List<ResumeUpload> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<ResumeUpload> findTopByUserIdOrderByCreatedAtDesc(Long userId);
}
