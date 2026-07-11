# Vendor Engine — Project Context Summary

---

## 1. Background

- **Course/assignment:** CS F213 OOP, BITS Pilani Summer 2026. Project: build a component for a
  digital festival-management ecosystem (Django/PostgreSQL/Redis/Celery backend, 5,000–6,000
  users, ₹1.5+ crore in wallet transactions). Students pick one of four tracks and implement a Java
  component that integrates with it. Two graded parts: **Part A** (problem formulation, ≤10 slides, 5
  marks) and **Part B** (full implementation + tests + demo video + report, 5 marks).
- **Track chosen:** Track 4 — Application Development with Java Frameworks. Specific problem:
  the vendor food-order dashboard example from the spec (order overload at peak hours, need for a
  real-time desktop dashboard with offline resilience).
- **Part A status:** already complete and submitted. The deck (`vendor_engine_deck__1_.pptx`,
  by Rohit Kumar Patel) defines the project as the **"High-Concurrency Vendor Engine"** — a Swing
  desktop app with autonomous offline failover, built on MVC + Producer-Consumer + Observer +
  Singleton patterns.
- **What was asked of me in this chat:** take that deck (Part A) plus the original spec PDF and produce
  everything needed to execute Part B — a full implementation architecture, then a build sequence
  tailored to the Antigravity agentic IDE, which is what you'll actually use to write the code.

## 2. Key facts locked in from Part A (do not deviate from these without good reason)

- **Design patterns:** MVC, Observer, Producer-Consumer, Singleton (all four explicitly named in the
  deck and tied to specific classes).
- **Concurrency model:** `ExecutorService.newFixedThreadPool(8)`, `LinkedBlockingQueue<String>`
  (capacity 1000) between producer and consumers, `ConcurrentHashMap<String, Stall>` for lock-free
  routing, per-stall `PriorityQueue<Order>` with a composite priority+wait-time comparator.
- **Offline failover:** `AppState implements Serializable`, `ObjectOutputStream`/`ObjectInputStream`
  via try-with-resources, a `NetworkMonitorDaemon` that's fully autonomous (no manual trigger).
- **Integration interfaces (three, per the deck):** file-based JSON exchange (`stalls.json` in,
  `sync_payload.json` out), simulated WebSocket via `BlockingQueue<String>`, binary serialization
  (`offline_stalls.ser`) as the offline cache, explicitly noted as "JDBC-ready" for a future real DB.
- **Success metrics to hit and be able to report on:** near-zero data loss on network drop, <100ms UI
  refresh at 50 orders/sec, ≥95% priority-ordering accuracy, <3s full recovery after reconnect, ≥8
  concurrent stall threads with no deadlocks.
- **Advanced Java features claimed (7+ in the deck):** concurrency, Observer, MVC, generics,
  serialization, lambdas, custom exceptions.

## 3. What I produced, and why, in order

### Doc 1 — `Vendor_Engine_Architecture.md`
The full Part B implementation spec. Turns the deck's high-level pattern choices into an exact,
unambiguous build reference: complete package tree (~22 classes), every class's fields/methods
written out, the concurrency threading table, GUI layout and Swing theming approach, file formats for
all three integration interfaces, a JUnit test plan, build/run instructions, a week-by-week task
breakdown, and a cheat sheet mapping every section back to the grading rubric.

Two decisions made here that go beyond what the deck specified, both flagged explicitly in the doc
and worth remembering:
- **`OrderComparator` must be a named class, not a lambda** — because `PriorityBlockingQueue`'s
  comparator needs to survive Java serialization (inside `AppState`), and lambdas aren't reliably
  serializable across JVM versions. This was framed as a genuine correctness fix, not just style, and
  is a good talking point for the report's "attention to correctness" angle.
- **A hand-rolled recursive-descent JSON parser (`JsonUtil`)** instead of pulling in `org.json`, since
  you asked to stay "pure Java" — framed as a legitimate extra advanced-feature to cite (a 9th
  technique beyond the deck's 7), with an explicit note that swapping to `org.json` is a fine
  alternative if week-3 time gets tight, as long as the trade-off is stated in the report.

### Doc 2 — `Vendor_Engine_Build_Guide.md`
A build-order guide once you told me you'd use **Antigravity** (Google's agentic IDE — Editor view +
Agent Manager for spawning/orchestrating agents, Planning mode vs Fast mode, Artifacts like
Implementation Plans and Walkthroughs for review) to actually write the code. I researched how
Antigravity works (Manager view, workspaces, Planning mode, browser-based verification, parallel
agents) before writing this, since a build guide is only useful if it matches the tool's real workflow.

Core judgment call in this doc: **don't use Antigravity's parallel-agent strength early.** The
architecture's layers have a strict dependency order (`model → exception → concurrency →
controller → io → view`), so running multiple agents at once early just causes stalls or conflicting
invented types. The guide sequences 8 stages, one agent at a time through Stage 6, with a specific
copy-pasteable prompt per stage, a verification checklist to run yourself before moving on, and only
opens up to genuinely parallel agents in Stage 7 (writing remaining tests, README, and an optional
bonus Strategy-pattern comparator) once the dependency chain is done and those tasks don't touch
overlapping files.

Also flagged: Swing is a desktop GUI, not a web app, so Antigravity's browser-based self-verification
doesn't apply to Stage 5 (the view layer) — that stage explicitly tells the agent it can't visually
confirm its own work, and hands you a manual checklist instead (login flow, live table updates, offline
banner, sync file write).

### This doc — `Vendor_Engine_Project_Context.md`
What you're reading now. A standing summary so nobody has to re-derive the reasoning above by
re-reading the whole thread.

## 4. What's still outstanding (not yet produced, flagged in the last exchange)

These weren't asked for yet, but were called out as gaps between "you can start building" and "you
can submit":

1. **The actual code.** Nothing has been built — the two docs above are the plan and the execution
   order, not the implementation. This is the real remaining work, done by running the Build Guide's
   stages through Antigravity.
2. **UML class diagram** — the spec explicitly requires one in the final report. Best generated once
   the real code exists (or from the architecture doc's class contracts if you want it earlier), so it
   reflects what was actually built rather than what was planned.
3. **Final report PDF** — the spec wants problem formulation + solution description + UML diagram
   submitted as one PDF. The architecture doc is a technical reference for building, not formatted as
   a submittable report; it'll need adapting once you know what shipped vs. what drifted during the
   build.
4. **Demo video (15–20 min)** — can't be pre-made; has to be recorded against the real running app.
   The Build Guide's Stage 5 verification checklist (login flow → live updates → offline toggle → sync
   file) is a reasonable on-camera script once the app works.
5. **README + repo/zip for submission** — Build Guide Stage 7 has an agent draft a README; worth a
   manual sanity pass once the real file structure exists, since agent-drafted docs can drift from
   what actually got built.

## 5. Suggested next step

Work through the Build Guide's Stage 0–6 with Antigravity to get a working app. Come back for the
UML diagram and the final report PDF once you know what actually got implemented — those two are
better generated from the real, finished code than from the plan, since implementation details always
shift a little during a 3-week build.
