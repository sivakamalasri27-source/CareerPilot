package com.careerpilot.repository;

import com.careerpilot.entity.AgentSuggestion;
import com.careerpilot.entity.SuggestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentSuggestionRepository extends JpaRepository<AgentSuggestion, Long> {

    List<AgentSuggestion> findAllByProfileId(Long profileId);

    List<AgentSuggestion> findAllByProfileIdAndStatus(Long profileId, SuggestionStatus status);

    Optional<AgentSuggestion> findByIdAndProfileId(Long id, Long profileId);

    Page<AgentSuggestion> findAllByProfileId(Long profileId, Pageable pageable);

    Page<AgentSuggestion> findAllByProfileIdAndStatus(Long profileId, SuggestionStatus status, Pageable pageable);

    long countByProfileIdAndStatus(Long profileId, SuggestionStatus status);
}
