package com.careerpilot.ai.service;

import com.careerpilot.entity.AiUsageLog;
import com.careerpilot.entity.User;
import com.careerpilot.repository.AiUsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenUsageTracker {

    private final AiUsageLogRepository aiUsageLogRepository;

    private static final BigDecimal INPUT_COST_PER_TOKEN = new BigDecimal("0.000000075");
    private static final BigDecimal OUTPUT_COST_PER_TOKEN = new BigDecimal("0.000000300");

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiUsageLog recordUsage(User user, String agentName, String modelName,
                                  int promptTokens, int completionTokens,
                                  long durationMs, boolean isSuccess) {
        int totalTokens = promptTokens + completionTokens;
        BigDecimal estimatedCost = calculateEstimatedCost(promptTokens, completionTokens);

        AiUsageLog usageLog = AiUsageLog.builder()
                .user(user)
                .agentName(agentName)
                .modelName(modelName)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .estimatedCostUsd(estimatedCost)
                .durationMs(durationMs)
                .isSuccess(isSuccess)
                .build();

        AiUsageLog saved = aiUsageLogRepository.save(usageLog);
        log.info("Recorded AI usage: agent={}, model={}, tokens={}/{}, duration={}ms, cost=${}, success={}",
                agentName, modelName, promptTokens, completionTokens, durationMs, estimatedCost, isSuccess);
        return saved;
    }

    public BigDecimal calculateEstimatedCost(int promptTokens, int completionTokens) {
        BigDecimal inputCost = BigDecimal.valueOf(promptTokens).multiply(INPUT_COST_PER_TOKEN);
        BigDecimal outputCost = BigDecimal.valueOf(completionTokens).multiply(OUTPUT_COST_PER_TOKEN);
        return inputCost.add(outputCost).setScale(6, RoundingMode.HALF_UP);
    }
}
