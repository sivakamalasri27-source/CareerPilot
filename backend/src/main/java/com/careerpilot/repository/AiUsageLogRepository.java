package com.careerpilot.repository;

import com.careerpilot.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    List<AiUsageLog> findAllByUserId(Long userId);

    List<AiUsageLog> findAllByAgentName(String agentName);
}
