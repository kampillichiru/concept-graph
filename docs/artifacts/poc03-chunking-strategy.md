# ADR 003: Chunking strategy for the concept notes

**Status:** accepted for POC 3, to be revisited with the POC 5 golden set.

## Context

The tutor answers questions from ~27 concept notes. Each note has a few sections (What it is, How it works, Pitfalls).
Retrieval returns chunks, and the answer can only cite what was retrieved, so the chunk is the unit of both
search quality and citation.

## Decision

**Chunk by Markdown section (`## ` heading).** One chunk per section, with the note title added to the text
that is embedded. The chunk keeps its heading as its citation label (`conceptId` + `section`).

## Options considered

| Option | How it works | Pro | Con |
|---|---|---|---|
| **Section (chosen)** | Split at `## ` headings | Meaningful unit, human-readable ids, matches how the notes are written | Section sizes vary; a section can be too big or too small |
| Fixed size | Every N characters, with overlap | Simple, uniform sizes, works on any text | Cuts sentences and definitions; ids are just offsets |
| Whole note | One chunk per file | Maximum context | Dilutes the embedding: one vector for three topics |

## Measured results (POC 3 run, this laptop, nomic-embed-text, qwen2.5:3b)

**Chunk sizes (section chunking):**

| Metric | Characters |
|---|---|
| Chunks | 81 (27 notes × 3 sections) |
| Min / median / max | 141 / 243 / 405 |
| Mean | 249 |

**Retrieval separation** (raw cosine from Qdrant): the relevant question scored 0.83–0.89 on its three chunks;
an off-topic question scored 0.74–0.75 on its best three.

**Answer quality on 5 in-scope questions:** with prompt v1, 4 of 5 cited the expected concept; the fifth cited
`[MERGE]`, which the grounding check flagged. With prompt v2, 5 of 5 cite the expected concept and the check finds no
unknown citations. Citation validity is measured in code, not by reading the answers.

## Measured: section vs fixed-size (`GET /poc03/compare-chunking`, k = 3)

Fixed-size baseline: 250-character windows, 50 overlap, ignoring headings (108 chunks). Same 8 labelled questions.

| Metric | SECTION | FIXED |
|---|---|---|
| Right concept in top-3 chunks (hit@k) | 8/8 | 8/8 |
| Top-3 chunk contains the answer phrase (keyword@k) | 8/8 | 8/8 |

**Result:** a tie on this set. Both strategies find the answer, so the measurement does not justify choosing one
by retrieval quality alone. The decision rests on two other points:

- **Citability:** SECTION chunks carry a human label (`How it works`). FIXED chunks only have character offsets,
  so a citation like `[cypher, chars 200-450]` tells the reader nothing.
- **Robustness (not measured):** fixed windows are the safer choice when a section is very long or has no headings,
  and their cut-off points can split a definition. Our notes are short and well-headed, which hides that weakness.

**Measurement history.** The first run gave 5/8 keyword hits for both. Two misses were measurement bugs: a phrase
split across a line break in the Markdown did not match, and one label said "merging" where the note says "merges".
Both were fixed before the reported result. The first run is not reported as a result.

## What we did not measure

- **Long sections.** The notes are all short, so the case where SECTION might lose is not in the data.
  To test it: add a note with a 2,000-character section and compare again.
- **Retrieval hit@k on a larger golden set.** 8 questions is too few to detect small differences. Grow the set in POC 5.
- **Answer quality per strategy.** Only retrieval was compared. The end-to-end answers were checked for the SECTION collection only.

## Consequences

- Citations are human-readable (`[chunking]`), and the code can check them against the retrieved ids.
- Ingestion is deterministic: point ids derive from the chunk id, so re-running is safe.
- A very long section would be retrieved as one large chunk. If that happens, split it further by paragraph
  inside the same heading, keeping the same `section` label.
- The embedding text includes the note title, so a chunk like "Pitfalls" is still about the right concept in the vector space.
- Changing the chunking means re-ingesting: the point ids depend on the section names, so renaming a heading
  creates a new chunk and leaves the old point behind. Delete stale points when that happens.
