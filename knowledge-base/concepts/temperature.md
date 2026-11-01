---
id: temperature
title: Temperature
category: Foundations
level: 1
requires: [llm]
related: [structured-output]
---
## What it is
Temperature is a setting that controls how random the model's word choices are. At 0 the model picks the most
likely token every time, so the same prompt gives nearly the same answer. Higher values (0.7 to 1.0) spread the
choices out and give more varied, more creative text.

## How it works
Before choosing a token, the model turns its scores into probabilities and divides them by the temperature.
Dividing by a small number makes the top choice dominate. Dividing by a large number flattens the distribution.

## Pitfalls
Temperature 0 is not a guarantee of truth. It only makes the answer repeatable. For factual answers from notes,
a low temperature is the right choice; for brainstorming, a higher one can help.
