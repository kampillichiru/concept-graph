---
id: llm
title: Large language model (LLM)
category: Foundations
level: 1
requires: []
related: [tokens-and-context-window, hallucination]
---
## What it is
A large language model is a neural network trained to predict the next token of text. Given a prompt,
it generates a continuation one token at a time. Chat models are LLMs tuned to follow instructions in a conversation.

## How it works
The model has no database of facts it looks up. Its knowledge is whatever patterns it absorbed during training,
frozen at a cut-off date. Each answer is a sequence of probability-weighted token choices, so the same prompt can
produce different text unless the randomness (temperature) is set to 0.

## Pitfalls
An LLM sounds confident whether or not it is right. It cannot see your files, your database or today's news
unless you put that text into the prompt. This is the reason retrieval-augmented generation exists.
