---
id: grounding-and-citations
title: Grounding and citations
category: Quality
level: 2
requires: [rag]
related: [hallucination]
---
## What it is
An answer is grounded when every claim can be traced to a source it was given. A citation is the visible link:
a label such as [embeddings] next to a claim, pointing to the note it came from.

## How it works
Label each chunk with its source id when you build the prompt. Instruct the model to cite the label after each
claim and to refuse when the context has no answer. Then check the output in code: are the cited ids among the
retrieved ids? A citation to an id that was never retrieved is a red flag.

## Pitfalls
A model can print a citation that looks right but does not support the sentence. Checking that the id exists is
cheap; checking that the sentence is supported needs a judge (see LLM-as-judge in the evaluation work).
