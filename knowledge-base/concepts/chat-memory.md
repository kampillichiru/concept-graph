---
id: chat-memory
title: Chat memory
category: Foundations
level: 1
requires: [llm, tokens-and-context-window]
related: [system-vs-user-messages]
---
## What it is
An LLM has no memory between requests. Chat memory is the application keeping the earlier messages of a
conversation and sending them again with each new question, so follow-ups like "give me an example of it" make sense.

## How it works
A common strategy is a message window: keep the last N messages per conversation id. Older messages are dropped,
so the prompt stays within the context window. The store (in memory here, a database in production) is separate
from the model, so it can be swapped.

## Pitfalls
A window forgets the start of a long conversation, including the instructions it had. Memory also keeps the
model's earlier mistakes, which it may repeat. Memory is about conversation history, not about knowledge: for facts,
use retrieval over your notes.
