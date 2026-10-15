package com.careerpilot.ai.client;

import com.careerpilot.ai.dto.LlmRequest;
import com.careerpilot.ai.dto.LlmResponse;

public interface LlmClient {

    String getProviderName();

    LlmResponse generate(LlmRequest request);
}
