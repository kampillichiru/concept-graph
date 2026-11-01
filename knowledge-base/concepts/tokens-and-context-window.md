---
id: tokens-and-context-window
title: Tokens and the context window
category: Foundations
level: 1
requires: [llm]
related: [chunking]
---
## What it is
A token is the unit an LLM reads and writes: often a word or part of a word. The context window is the maximum
number of tokens the model can handle in one request, counting the prompt, the chat history and the answer together.

## How it works
Text is split into tokens by a tokenizer before the model sees it. Roughly, one English word is about 1.3 tokens.
Everything you send must fit in the window. If the window is full, the provider rejects the request or cuts text.
Longer prompts also cost more time and memory on the model server.

## Pitfalls
Putting ten long documents in a prompt "because it fits" makes answers slower and sometimes worse: the model
can lose details in the middle of a long context. Retrieving only the few relevant chunks is usually better.
