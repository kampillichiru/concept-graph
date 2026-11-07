# AI Solution Architect Learning Curve – Knowledge & Hands-On

> **Goal:** Build working architect-level knowledge of Claude-based systems and prove it with hands-on projects. No certification exam is planned.
> **Target roles:** Forward Deployed Engineer (FDE) / AI Solution Architect.
> **Learner profile:** Senior Java engineer. Java only, no Docker, Claude Pro subscription only (no API keys).
> **Pace:** 8 weeks at 8–10 hours per week.
> **Reference:** The third-party CCA-F domain list below is used only as a topic syllabus, not as an exam target.

---

## 1. Architect Outcomes

By the end of Week 8 you should be able to:

- [ ] Choose an agent pattern (single agent, orchestrator/workers, pipeline) for a business problem and defend it
- [ ] Design tool and MCP interfaces that a model can use reliably
- [ ] Manage context budgets, memory, and retrieval for multi-step workflows
- [ ] Set up Claude Code as a governed, team-wide engineering tool
- [ ] Evaluate an AI feature with a repeatable test set and track regressions
- [ ] Write architecture decision records (ADRs) and draw system diagrams a client can read
- [ ] Explain cost, latency, security, and failure-mode trade-offs to non-engineers
- [ ] Demo a working system end to end

---

## 2. Knowledge Syllabus

| # | Topic area | Key knowledge | Main weeks |
|---|---|---|---|
| 1 | Agentic architecture | Patterns, loop control, human checkpoints, failure recovery | 2, 5 |
| 2 | Claude Code configuration & workflows | Settings scopes, `CLAUDE.md`, permissions, hooks, subagents, headless, CI | 1, 6 |
| 3 | Prompt engineering | Structure, structured output, few-shot, evaluation | 3 |
| 4 | Tool design & MCP | Tool schemas, error shapes, least privilege, MCP servers and clients | 4 |
| 5 | Context management | Budgets, compaction, pin vs retrieve, subagent isolation | 5 |
| 6 | Architecture practice | ADRs, diagrams, cost/latency/security trade-offs, stakeholder demos | 7, 8 |

---

## 3. Learning Curve at a Glance

```
Week 0  ▓░░░░░░░░  Scope, environment, architect templates
Week 1  ▓▓░░░░░░░  Claude Code configuration         (Topic 2)
Week 2  ▓▓▓░░░░░░  Agentic architecture              (Topic 1)
Week 3  ▓▓▓▓░░░░░  Prompt engineering & evaluation    (Topic 3)
Week 4  ▓▓▓▓▓░░░░  Tool design & MCP                 (Topic 4)
Week 5  ▓▓▓▓▓▓░░░  Context management + agent review  (Topics 5, 1)
Week 6  ▓▓▓▓▓▓▓░░  Claude Code in delivery pipelines (Topic 2 applied)
Week 7  ▓▓▓▓▓▓▓▓░  Integrate POCs + architecture docs (Topic 6)
Week 8  ▓▓▓▓▓▓▓▓▓  Demo, review, and portfolio write-up
```

---

## 4. Week-by-Week Plan

### Week 0 – Scope & Setup
**Goal:** Fix the scope and working setup before studying.

- [ ] Confirm the Claude Pro setup and the Claude Code install on this laptop
- [ ] Create the ADR template and diagram conventions (see Section 6)
- [ ] Create a study and build log (date, hours, what was built, decisions made)
- [ ] Confirm the three POCs in Section 5 with the owner

**Done when:** The ADR template exists and the three POCs are approved for design.

---

### Week 1 – Claude Code Configuration (Topic 2)
**Goal:** Configure Claude Code the way a team would run it.

**Topics**
- [ ] Settings scopes: user, project, local
- [ ] `CLAUDE.md` hierarchy and what belongs in each level
- [ ] Permission rules (allow / ask / deny)
- [ ] Hooks: what triggers them and what they can block
- [ ] Subagents and custom slash commands
- [ ] Plan mode and when to use it
- [ ] Headless mode (`claude -p`) for scripting

**Hands-on**
- [ ] Set up a throwaway Java project with all of the above

**Architect check**
- [ ] Explain why a rule belongs in project settings and not user settings
- [ ] Explain what a hook can do that `CLAUDE.md` cannot

---

### Week 2 – Agentic Architecture (Topic 1)
**Goal:** Design multi-step agent systems with clear control flow.

**Topics**
- [ ] Single agent vs orchestrator + subagents: when to split
- [ ] Loop termination and max-step guards
- [ ] Human-in-the-loop checkpoints
- [ ] Error recovery and retries
- [ ] Cost and latency trade-offs

**Hands-on**
- [ ] Write one design note per pattern (single agent, orchestrator/worker, pipeline)

**Architect check**
- [ ] Given a business scenario, pick a pattern and justify it in three sentences

---

### Week 3 – Prompt Engineering & Evaluation (Topic 3)
**Goal:** Write prompts that are testable and stable.

**Topics**
- [ ] System vs user prompt placement
- [ ] Structured output (JSON schema, tagged output)
- [ ] Few-shot example design
- [ ] Prompt evaluation against a fixed test set
- [ ] Failure modes: hallucinated fields, ignored constraints

**Hands-on**
- [ ] Build a 20-case test set for one extraction prompt and track pass rate across two revisions

**Architect check**
- [ ] Explain how you would detect a prompt regression before shipping

---

### Week 4 – Tool Design & MCP (Topic 4)
**Goal:** Design tools the model can use correctly and safely.

**Topics**
- [ ] Tool schemas and descriptions (the description is the model's interface)
- [ ] Error shapes the model can act on
- [ ] Least-privilege tool sets
- [ ] MCP concepts: servers, clients, resources, tools, transports
- [ ] Java MCP SDK basics (confirm the current artifact first)

**Hands-on**
- [ ] Sketch a 3–4 tool MCP server design for a read-only graph lookup

**Architect check**
- [ ] Explain why a vague tool description causes wrong tool calls

---

### Week 5 – Context Management (Topic 5) + Agent Review (Topic 1)
**Goal:** Keep context useful and within budget.

**Topics**
- [ ] Context budgeting per agent
- [ ] Summarisation and compaction
- [ ] What to pin in context vs retrieve on demand
- [ ] Subagents as context isolation

**Hands-on**
- [ ] Write a context budget for one multi-step workflow
- [ ] Review the Week 2 agent designs against what you learned about context

**Architect check**
- [ ] Explain when a subagent reduces context pressure and when it adds overhead

---

### Week 6 – Claude Code in Delivery Pipelines (Topic 2 applied)
**Goal:** Apply Claude Code across a team without Docker.

**Topics**
- [ ] Headless runs and output formats
- [ ] CI usage with a runner you control (no Docker)
- [ ] Review and guardrail hooks
- [ ] Governance: shared `CLAUDE.md`, permission baselines, review rules

**Hands-on**
- [ ] Add one guardrail hook and one headless job to a local project

**Architect check**
- [ ] Describe a rollout plan for Claude Code across a team of five engineers

---

### Week 7 – Integrate & Document (Topic 6)
**Goal:** Connect the POCs and document them the way a client would expect.

**Topics**
- [ ] ADRs: context, options, decision, consequences
- [ ] Diagrams: context, container, and sequence views
- [ ] Cost model per request and per workflow
- [ ] Security review: data handling, tool permissions, prompt injection risk

**Hands-on**
- [ ] Write ADRs for the main decisions in POC-1, POC-2, and POC-3
- [ ] Draw one architecture diagram per POC

**Architect check**
- [ ] Present the trade-offs of one design to someone without an engineering background

---

### Week 8 – Demo, Review & Portfolio
**Goal:** Show the work end to end.

- [ ] Demo the three POCs as one system
- [ ] Re-run the Week 3 evaluation set and record final scores
- [ ] Write a one-page case study: problem, design, trade-offs, results, what you would change
- [ ] Update the outcomes checklist in Section 1

---

## 5. Hands-On Projects

Each project is built only after its design is approved.

| POC | Project | Covers | Deliverable |
|---|---|---|---|
| POC-1 | Claude Code setup on LoanPilot | Weeks 1, 6 | Layered `CLAUDE.md`, permission rules, one review hook, one subagent |
| POC-2 | Java MCP server for ConceptGraph | Week 4 | 3–4 read-only tools over Neo4j, strict schemas, clear error shapes |
| POC-3 | Orchestrator with two subagents | Weeks 2, 5 | Planner + executor, context budgets, termination rule |

**Open decision:** POC-2 uses a natively installed Neo4j, or a file-backed graph for the MCP exercise.

---

## 6. Architect Templates

### ADR template

```
# ADR-NNN: <decision title>
Status: Proposed | Accepted | Superseded
Context: what forces the decision
Options: 2–3 realistic options with trade-offs
Decision: the option chosen and why
Consequences: cost, latency, risk, what becomes harder
```

### Diagram conventions
- **Context view:** users, Claude Code, MCP servers, data stores, external systems
- **Container view:** services, agents, tool servers, storage
- **Sequence view:** one key user flow, including failure paths

---

## 7. Resources

| Resource | Use in plan | Link |
|---|---|---|
| Anthropic documentation (Claude Code, MCP, tool use) | Weeks 1–6 | Verify current pages |
| Anthropic Academy (Skilljar) | Optional, as needed | Verify course list |
| Third-party CCA-F guides | Topic cross-check only | [CCA-F complete guide](https://rickhigh.substack.com/p/claude-certified-architect-the-complete), [2026 guide](https://pasqualepillitteri.it/en/news/3297/claude-certified-architect-guide-2026-en) |

---

## 8. Progress Tracker

| Week | Planned hours | Actual hours | Architect check passed? | Built / documented | Notes |
|---|---|---|---|---|---|
| 0 | 8 | | | | |
| 1 | 9 | | | | |
| 2 | 9 | | | | |
| 3 | 9 | | | | |
| 4 | 9 | | | | |
| 5 | 9 | | | | |
| 6 | 9 | | | | |
| 7 | 10 | | | | |
| 8 | 6 | | | | |

---

## 9. Decisions Pending

- [ ] POC-2 Neo4j approach (native install vs file-backed graph)
- [ ] Portfolio target: private case studies, or public write-ups
- [ ] Confirm weekly hours (8–10 assumed)
