---
id: ai-agent
title: AI agent
category: Agents
level: 3
requires: [llm, tool-calling]
related: [mcp, langgraph]
---
## What it is
An AI agent is an LLM in a loop that can take actions. It reads the goal, decides on a step, calls a tool,
observes the result, and repeats until it can answer. The model chooses the steps; the code provides the tools.

## How it works
The common pattern is ReAct: reason about what to do next, act by calling a tool, observe the output, and
reason again. A limit on the number of iterations stops the loop from running forever. Each step is one or more
LLM calls, so an agent is slower and more expensive than a single prompt.

## Pitfalls
An agent can choose the wrong tool or loop without progress. Vague tool descriptions cause wrong choices. Ask
whether a fixed workflow would do the job before reaching for an agent.
