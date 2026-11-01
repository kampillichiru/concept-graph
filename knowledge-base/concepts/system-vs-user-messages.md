---
id: system-vs-user-messages
title: System vs user messages
category: Foundations
level: 1
requires: [llm]
related: [structured-output, prompt-injection]
---
## What it is
A chat request is a list of messages with roles. The system message sets the rules and the persona ("You are a
tutor. Cite your sources."). The user message carries the question or the input. The model is trained to give
system instructions more weight than user text.

## How it works
In LangChain4j, an AI Service method can be annotated with @SystemMessage (fixed rules) and takes its arguments
as the user message. Put stable instructions in the system message and changing data in the user message.

## Pitfalls
User text can contradict the system message, and sometimes wins. Never treat a system message as a security
boundary: anything the user can type, or any retrieved document, can try to override it.
