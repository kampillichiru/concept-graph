---
id: langgraph
title: LangGraph (state machines for LLM apps)
category: Agents
level: 3
requires: [ai-agent]
related: [tool-calling]
---
## What it is
LangGraph models an LLM workflow as a graph: nodes are steps, edges are transitions, and a shared state flows
through them. Conditional edges choose the next step from the state. LangGraph4j is the Java version.

## How it works
Each node reads the state and returns an update. Conditional edges route on the state, for example "if the answer
is not grounded, retry once". Checkpoints save the state per thread, so a run can pause, for example for a human,
and resume later.

## Pitfalls
Graphs make the flow explicit, but they add structure you must maintain. Every loop needs a limit in code, or it
can run forever.
