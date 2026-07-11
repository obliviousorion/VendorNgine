# Vendor Engine — Build Guide for Antigravity
### How to actually build Section 3–15 of the architecture doc, stage by stage, using Antigravity's Agent Manager

This guide assumes you have `Vendor_Engine_Architecture.md` (the previous doc) sitting in your project
folder as the single source of truth. Antigravity agents read files in the workspace, so **step 0 is
getting that file into the repo before you dispatch a single agent** — everything else builds on it.

A note on how this guide is organized: Antigravity's big advantage (parallel agents in Manager view)
is actually a trap for this specific project. The layers have a strict dependency order — `model` →
`exception` → `concurrency` → `controller` → `io` → `view` → `test` — and each layer's classes need the
one below it to already compile. Running five agents on five layers at once means four of them stall
waiting for interfaces that don't exist yet, or worse, each agent invents its own version of a shared
type and you spend longer reconciling merge conflicts than you saved. **Run one agent at a time,
sequentially, in Planning mode, and only start the next stage after you've reviewed the previous one's
Artifacts.** Save parallel agents for genuinely independent work later (Section 7 below).

---

## Stage 0 — Project setup (you do this manually, 10 minutes)

1. Create the project folder locally, e.g. `~/projects/vendor-engine`, and open it in Antigravity as a
   **Workspace** (not the Playground — you want this persisted, not ephemeral).
2. Drop these files into the workspace root before touching any agent:
   - `Vendor_Engine_Architecture.md` (the full architecture doc)
   - `OOP_Project_Specifications.pdf` (the original handout, for citation-of-requirements)
   - an empty `data/` folder
3. Initialize git immediately: `git init && git add . && git commit -m "docs: architecture + spec"`.
   Commit after every stage below — Antigravity doesn't have branch support mature enough to rely on
   its own history; your own git commits are the real undo button.
4. In Antigravity's mode selector, set **Agent-driven / Autopilot** off for now — use
   **Agent-assisted** so it still asks before anything destructive, until you trust the pattern. You
   can loosen this once Stage 1–2 prove reliable.
5. Set Terminal Policy to **Auto** for standard commands (`mvn`, `git`, `java`) so you're not
   approving every single build — but keep confirmation on for anything that deletes files.

---

## Stage 1 — Scaffold + Model layer

**Why first:** nothing else compiles without `Order`, `Stall`, `AppState`, and the enums existing.
Zero business logic here, so it's the safest stage to hand over with high autonomy.

**Open a new task in Manager view, Planning mode, prompt:**
```
Read Vendor_Engine_Architecture.md fully before doing anything.

Scaffold a Maven project at the workspace root matching Section 3 of that document exactly:
group id com.festival.vendorengine, Java 17, JUnit 5 (test scope only — no other dependencies).
Use the exact pom.xml given in Section 3.

Then implement ONLY the model/ and exception/ packages (Sections 4 and 5): OrderStatus, UserRole,
PriorityToken, Order (with the static nested LineItem class), Stall, AppState, and the three custom
exceptions. Follow the field/method signatures given exactly — do not add fields that aren't
specified. Every class in model/ must have zero imports from javax.swing or java.awt — this is a hard
constraint, not a suggestion.

Do not write OrderComparator yet (it belongs to controller/, Section 6.4) — Order.compareTo can
throw UnsupportedOperationException as a placeholder for now.

When done, run `mvn compile` and fix any errors until it's clean. Report back with a summary of every
file created.
```
**Verify before proceeding:** open the Walkthrough artifact, skim every file it lists, and specifically
grep for `javax.swing` inside `model/` — should return nothing. Run `mvn compile` yourself once more.
Commit: `git commit -am "feat: model + exception layer"`.

---

## Stage 2 — Concurrency layer

**Why second:** the producer/consumer/executor classes only depend on `model/` and `exception/`,
which now exist. This is the stage most likely to need a correction round, because thread-safety bugs
don't show up as compiler errors — read the Walkthrough carefully here, don't rubber-stamp it.

**Prompt:**
```
Read Vendor_Engine_Architecture.md, Section 6, again before starting — pay particular attention to
6.4 (the PriorityBlockingQueue serialization gotcha).

Implement the concurrency/ package: ExecutorServiceManager (eager singleton, fixed pool of 8),
OrderProducer, OrderConsumer, PeakHourSimulator, and MockDataLoader.

Also implement controller/OrderComparator now (even though it's technically a controller/ class per
Section 3's package tree) — the concurrency layer's Stall queue needs it, and it MUST be a named
class implementing Serializable, not a lambda, exactly per Section 6.4's reasoning. Explain in a code
comment why it's not a lambda.

PeakHourSimulator should default to 10 orders/sec, support a "spike mode" of 50/sec, and jitter the
sleep interval ±20%.

Write concurrency/OrderProducerConsumerTest per Section 13's spec: push 500 synthetic payloads
through a real LinkedBlockingQueue + 8 consumers, use a CountDownLatch(500) with a 5-second
timeout, assert all 500 land in some stall's queue with none lost or duplicated. Run `mvn test` and
make it pass before reporting back.
```
**Verify:** actually read the test the agent wrote — a common failure mode for agents on
concurrency tests is asserting on total count without checking for duplicates, which would hide a bug
where the same order gets routed twice. Re-read `OrderComparator` and confirm it's a named class.
Commit.

---

## Stage 3 — Controller layer

**Why third:** needs `model/`, `exception/`, and `OrderComparator` (already done in Stage 2).

**Prompt:**
```
Implement controller/OrderObserver (interface), controller/OrderController, and
controller/StallDataSource per Section 7 of Vendor_Engine_Architecture.md.

Critical constraint: OrderController is the ONLY class in the whole project allowed to call
Order.setStatus(...). Enforce legal transitions only: PENDING -> ACCEPTED -> READY -> SERVED, no
skipping steps, and reaching SERVED must call stall.addRevenue(...) with the order's total.

Use CopyOnWriteArrayList for the observers list, and wrap every observer notification in
SwingUtilities.invokeLater per Section 7.2 — even though no view exists yet, stub this correctly now
so Stage 5 doesn't need to touch this file again.

Write controller/OrderControllerTest and controller/OrderComparatorTest per Section 13.
Run `mvn test`, all green, before reporting back.
```
**Verify:** this is the stage where "no deadlocks" claims live or die — check that nothing here calls
`synchronized` on anything other than what `Stall` already does internally. Confirm the illegal-
transition test actually asserts an exception/rejection, not just that the method runs.

---

## Stage 4 — IO / storage layer

**Why fourth:** depends on `model/` (`AppState`) and needs to exist before `NetworkMonitorDaemon`
is wired into the controller in Stage 5.

**Prompt:**
```
Implement io/JsonUtil (the hand-rolled recursive-descent parser from Section 8.1 — parseOrder and
the supporting parseObject/parseArray/parseValue methods), io/OfflineSerializer,
io/NetworkMonitorDaemon, and io/SyncClient, per Section 8.

NetworkMonitorDaemon must run as a daemon thread (setDaemon(true)) submitted OUTSIDE the
8-thread ExecutorServiceManager pool — it must never be starved by busy consumers. It should read a
`network.flag` file each heartbeat tick (content "up" or "down") to simulate connectivity for demo
purposes, per Section 14's manual-toggle note.

Write io/OfflineSerializerTest (save/load round trip using @TempDir, plus a corrupted-file case that
must throw OfflineSerializationException, not a raw IOException) and
model/AppStateSerializationTest (build a 2-stall/3-order AppState, serialize, deserialize, assert deep
equality) per Section 13.

Run `mvn test`, all green, before reporting back.
```
**Verify:** manually corrupt a `.ser` file (truncate it with `truncate -s 10 offline_stalls.ser`) and
confirm the app throws the custom checked exception, not a stack trace to the console.

---

## Stage 5 — View layer (Swing)

**Why fifth, and why it's the biggest stage:** everything it needs (`OrderController`,
`StallDataSource`, `AppState`) now exists. This is also the one stage where Antigravity's browser
verification **does not apply** — Swing is a desktop GUI, not a web page, so the agent cannot visually
self-verify the way it would for a web app. Say this explicitly in the prompt or it may waste time
trying to launch a browser.

**Prompt:**
```
Implement view/UiTheme, view/LoginView, view/OrderTableModel, view/StallPanel, view/KitchenView,
and view/AdminView per Section 9.

Note: this is a Swing desktop app, not a web app — you do not have browser-based visual verification
available for this stage. Instead, after building, run the app via `mvn package && java -jar
target/vendor-engine-1.0.jar` in the terminal and confirm it launches without exceptions in the
console; I will visually verify the UI myself.

Wire OrderTableModel as an OrderObserver exactly as Section 9.2 describes — it must only ever be
updated from the EDT (this is already guaranteed by OrderController's invokeLater wrapping from
Stage 3, so OrderTableModel itself needs no additional threading logic).

Add the non-static inner class OrderRowRenderer inside StallPanel per Section 4.4's inner-class
example — this satisfies the "inner classes" rubric requirement distinctly from LineItem's static
nested class.

Call UiTheme.apply() as the very first line of Main.main(), before any frame is constructed, and start
the GUI via SwingUtilities.invokeLater per Section 9.5.
```
**Verify (this stage you must do by hand, an agent can't fully self-check a GUI):**
1. Run it. Log in as `KITCHEN_WORKER`. Confirm the table updates live as simulated orders arrive.
2. Log in as `MERCHANT_ADMIN`. Confirm aggregate revenue/queue-depth numbers move.
3. Resize the window, switch stalls, confirm nothing throws to console.
4. `echo down > network.flag`, confirm the offline banner appears within ~1s (Stage 4's daemon).
5. `echo up > network.flag`, confirm it clears and `sync_payload.json` gets written.

If the UI looks generically default-grey rather than matching `UiTheme`'s palette, that's a real
finding — send it back to the agent with a specific note ("the dark palette from UiTheme isn't
applying to the JTable header, fix that specifically") rather than a vague "make it look better."

---

## Stage 6 — Wire Main.java + shutdown hook + end-to-end smoke test

**Prompt:**
```
Implement Main.java and AppConfig.java. Main should: call UiTheme.apply(), construct the shared
OrderController/stallMap/AppState, start OrderProducer + 8 OrderConsumers via
ExecutorServiceManager, start NetworkMonitorDaemon as a separate daemon thread, register a JVM
shutdown hook that calls ExecutorServiceManager.getInstance().shutdown(), and finally launch
LoginView via SwingUtilities.invokeLater.

Run the full app for 60 seconds with PeakHourSimulator in spike mode (50 orders/sec) and confirm in
the terminal output that no exceptions are thrown and the process shuts down cleanly on window
close (no lingering non-daemon threads keeping the JVM alive).
```
**Verify:** after closing the window, check the terminal actually returns to the prompt (doesn't hang)
— that's your proof the shutdown hook and daemon thread setup are both correct.

---

## Stage 7 — Now parallelize (this is where multiple agents genuinely help)

Once Stages 1–6 are done and committed, the remaining work is independent enough to actually use
Manager view's multi-agent strength. Spawn these **at the same time**, each as its own task:

- **Agent A:** "Write the remaining JUnit tests from Section 13 not yet covered, and get coverage on
  `model/`, `controller/`, and `io/` to ≥80% (measure with `mvn jacoco:report` if you add the plugin, or
  just enumerate branch coverage manually and report the estimate)."
- **Agent B:** "Write `data/stalls.json` and `README.md` per Sections 12 and 14 — the README must
  include the exact build/run/test commands from Section 14, verbatim."
- **Agent C (optional, bonus):** "Add a `RevenueWeightedComparator` alternative to
  `OrderComparator` and wire it as a Strategy-pattern swap-in per Section 10's bonus row — don't
  change the default behavior, just add the option and a short note in the README on how to switch."

These don't touch overlapping files (tests / docs / one new comparator class), so running them
concurrently is safe and is exactly the workflow Antigravity's Manager view is built for. Review each
one's Artifacts independently before merging; commit each stage's work separately so a bad one is
easy to revert without losing the other two.

---

## Stage 8 — Final polish pass (single agent, Fast mode is fine here)

By now everything is functionally done; this stage is cosmetic/robustness cleanup, low risk:
```
Do a final pass: run `mvn clean verify`, fix any warnings, ensure every catch block either logs with
context or rethrows a custom exception (none should be empty or print raw stack traces to a user-
facing console). Double check UiTheme is applied consistently across LoginView, KitchenView, and
AdminView. Confirm offline_stalls.ser and sync_payload.json get written to the project root (not a
random working directory) regardless of how the jar is launched.
```

---

## Practical Antigravity tips specific to this project

- **Always Planning mode for Stages 1–6.** These build on each other; you want to see the
  Implementation Plan artifact and catch a wrong assumption (e.g., the agent deciding to use a lambda
  comparator despite Section 6.4's explicit warning) *before* code gets written, not after.
- **Feed the architecture doc by reference, not by re-pasting it.** Every prompt above starts with
  "Read Vendor_Engine_Architecture.md" — Antigravity's agents read workspace files directly, so you
  don't need to paste sections into the chat. If an agent seems to ignore a specific constraint,
  quote the exact section number back at it in a follow-up comment on the Artifact.
- **Use the Problems panel's "Explain and Fix" for compiler errors** rather than describing the error
  yourself — faster, and it has full context already.
- **Don't use "Always proceed" browser JavaScript execution for this project** — there's no web
  frontend, so you don't need it, and it's an unnecessary permission to grant.
- **Comment directly on Artifacts instead of starting new chat turns** when a stage is 90% right —
  e.g., on the Stage 5 Walkthrough, comment on the specific screenshot/file if the JTable header color
  is wrong, rather than re-prompting from scratch. This keeps the agent's context intact.
- **One workspace, one git repo, commit after every stage.** If Stage 4 goes sideways, you want to
  `git reset --hard` to the end of Stage 3, not untangle which files came from which agent run.

---

## Full build order at a glance

```
Stage 0  Project setup (manual)
Stage 1  model/ + exception/                    ← foundation, zero UI, safest to automate fully
Stage 2  concurrency/ + OrderComparator          ← thread-safety critical, read the Walkthrough closely
Stage 3  controller/                             ← the "only mutator" rule lives here
Stage 4  io/                                     ← serialization + daemon + JSON parsing
Stage 5  view/                                   ← manual visual verification required, no browser agent
Stage 6  Main.java wiring + smoke test
Stage 7  parallel: tests / docs / bonus pattern  ← safe to run 2-3 agents at once, finally
Stage 8  final polish pass
```

Follow this order and every class in the architecture doc's Section 3 tree gets built exactly once,
with each stage's automated tests passing before the next stage starts — by Stage 6 you have a fully
working, demoable app, and Stage 7's parallel agents are pure upside rather than risk.
