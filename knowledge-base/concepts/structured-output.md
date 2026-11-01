---
id: structured-output
title: Structured output
category: Foundations
level: 1
requires: [llm, system-vs-user-messages]
related: [temperature]
---
## What it is
Structured output means the model returns data in a fixed shape, such as JSON that maps onto a Java record,
instead of free text. Code can then read the fields directly.

## How it works
You describe the shape: a JSON schema, or a Java type that the framework turns into a schema. The provider
constrains the model's output to match it, or the framework parses the text and retries if it does not. In
this project the ConceptCard record from POC 1 works this way.

## Pitfalls
Schema-valid is not the same as correct. A model can fill every field with plausible invented values. Validate
meaning as well as shape, and keep the schema small: deeply nested objects are harder for small models.
