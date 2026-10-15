package com.careerpilot.agent.core;

public interface Agent<I, O> {

    String getName();

    AgentResult<O> execute(AgentContext<I> ctx);
}
