---
id: tool-calling
title: Tool calling
category: Agents
level: 3
requires: [llm, structured-output]
related: [ai-agent, mcp]
---
## What it is
Tool calling lets the model request a function call instead of writing an answer. You declare the tools with a
name, a description and typed parameters. The model returns "call searchNotes with query X", your code runs it,
and the result goes back to the model.

## How it works
The tool description is part of the prompt, so it shapes the model's choice as much as the code does. The model
produces arguments that match the parameter types, which the framework validates. Tool errors should be returned
as text, so the model can recover instead of crashing the request.

## Pitfalls
A tool that changes data is dangerous when the model chooses it. Make read-only tools the default, validate every
argument in code, and never trust the model to enforce permissions.
