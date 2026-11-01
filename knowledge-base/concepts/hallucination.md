---
id: hallucination
title: Hallucination
category: Quality
level: 2
requires: [llm]
related: [rag, grounding-and-citations]
---
## What it is
A hallucination is an answer that sounds right but is false or invented: a made-up function name, a fake
citation, a wrong date. It happens because the model generates the most plausible continuation, and plausibility
is not truth.

## How it works
When the model lacks the knowledge, it does not stop by default. It keeps predicting likely text. The
risk is highest for niche topics, recent events, private data and precise details such as numbers and names.

## Pitfalls
You cannot tell a hallucination from a correct answer by its tone. The fixes are architectural: give the model
the facts (RAG), force it to cite them (grounding), tell it to say "I don't know", and measure the rate with
an evaluation set.
