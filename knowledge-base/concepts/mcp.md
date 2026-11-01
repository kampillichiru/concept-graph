---
id: mcp
title: Model Context Protocol (MCP)
category: Agents
level: 3
requires: [tool-calling]
related: [ai-agent, prompt-injection]
---
## What it is
The Model Context Protocol (MCP) is an open standard for connecting AI clients to tools, data and prompt
templates. A server exposes its capabilities; any MCP-capable client, such as Claude Code, can discover and use them
without custom integration code for each client.

## How it works
A host (the application) runs one or more clients. Each client talks JSON-RPC to one server, over stdio for local
processes or Streamable HTTP for remote ones. A server offers three primitives: tools (actions), resources (data
you can read) and prompts (reusable templates).

## Pitfalls
Everything a server returns enters the client's context window, so large payloads are costly. Tool descriptions
from a server are trusted by the model, which makes a malicious server a prompt-injection risk. Start with
read-only tools.
