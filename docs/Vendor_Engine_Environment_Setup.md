# Vendor Engine — Environment & IDE Setup
### Everything to install and configure before Stage 0 of the Build Guide

Do this once, in order. Each step has a "verify" line — don't move to the next step until that check
passes, since a bad JDK/Maven install is the single most common reason an agent's `mvn compile`
loops fail silently for several turns before you notice.

---

## 1. Java Development Kit (JDK 17)

The architecture doc's `pom.xml` targets Java 17 — pick this, not whatever your OS ships by default.

- **Windows/Mac/Linux:** install via [Adoptium Temurin 17](https://adoptium.net/temurin/releases/?version=17)
  (the free, standard OpenJDK build most course environments expect). Pick the `.msi` (Windows),
  `.pkg` (Mac), or your distro's package (Linux, e.g. `sudo apt install temurin-17-jdk` if you've added
  the Adoptium apt repo, or `sdk install java 17.0.13-tem` if you use SDKMAN — SDKMAN is the easiest
  route on Mac/Linux if you might need to juggle JDK versions later).
- **Verify:**
  ```
  java -version
  javac -version
  ```
  Both must report `17.x`. If you have multiple JDKs installed and an older one wins, fix `JAVA_HOME`
  and your `PATH` before continuing — don't work around it with `mvn -Dmaven.compiler.source=...`
  flags, since Antigravity's agent won't know to add those flags on every command it runs.

## 2. Maven

- **Windows:** download the binary zip from [maven.apache.org](https://maven.apache.org/download.cgi),
  extract, add `bin/` to `PATH`.
- **Mac:** `brew install maven`.
- **Linux:** `sudo apt install maven` (or your distro's equivalent).
- **Verify:**
  ```
  mvn -version
  ```
  Confirm it reports Java 17 as the runtime it's using (Maven prints this in its output) — if it's
  pointing at a different JDK, fix `JAVA_HOME` again.

## 3. Git

- Install from [git-scm.com](https://git-scm.com/downloads) if not already present.
- Set your identity once, globally, so every commit the agent makes (or you make) is attributable:
  ```
  git config --global user.name "Your Name"
  git config --global user.email "you@example.com"
  ```
- **Verify:** `git --version`.

## 4. Google Antigravity

- Download from [antigravity.google/download](https://antigravity.google/download) — available for
  macOS, Windows, and Linux, free in public preview with a personal Google account (no Workspace
  account needed).
- Sign in with a personal Gmail account when prompted.
- During first-run setup you'll be asked "who's driving the car" — pick **Agent-assisted** for now
  (recommended default: you stay in control, agent handles safe automations, asks before anything
  destructive). You can switch to full Autopilot later once Stages 1–3 have proven reliable, per the
  Build Guide's advice.
- Set **Terminal Policy** to **Auto** for standard commands — this lets the agent run `mvn compile`,
  `mvn test`, `git status`, etc. without stopping to ask you every time, which is what the Build Guide's
  stage prompts assume. Leave destructive-action confirmation (file deletion, force-push) turned on.
- Skip the browser extension install for now — the Build Guide notes this project has no web
  frontend, so you won't need Antigravity's browser-verification surface at all.
- **Verify:** open Antigravity, and in the Manager view confirm you can select **Planning mode** vs
  **Fast mode** in the task-creation screen — if you only see one mode, you're on an older version;
  update before starting Stage 1.

## 5. Open the project as an Antigravity Workspace (not the Playground)

- Create the folder locally first: e.g. `~/projects/vendor-engine`.
- In Antigravity, choose **Open Workspace** (a persisted project folder) rather than **Playground**
  (an ephemeral sandbox) — you want git history and file state to survive between agent sessions.
- Copy in before dispatching any agent:
  - `Vendor_Engine_Architecture.md`
  - `Vendor_Engine_Build_Guide.md`
  - `OOP_Project_Specifications.pdf`
  - an empty `data/` folder
- Run inside that folder:
  ```
  git init
  git add .
  git commit -m "docs: architecture, build guide, spec"
  ```

## 6. Machine resource check (matters for an 8-thread pool + Swing GUI + agent overhead)

- Antigravity's AI features need an active internet connection at all times — the editor/terminal
  work offline, but agent dispatch, autocomplete, and chat do not. Don't plan on building this on a
  train with spotty Wi-Fi.
- 8 background consumer threads plus a Swing EDT plus Antigravity itself is light for any machine
  from the last several years, but if you're on a resource-constrained laptop, close other heavy apps
  while an agent is mid-task — Antigravity's own processing happens in the cloud, but your local
  `mvn` builds and the running Swing app are on your CPU.
- Make sure your screen resolution is something reasonable (1080p+) before the demo-recording
  stage — a cramped, tiny Swing window looks unpolished on video regardless of how good the code is.

## 7. Optional but recommended: a screen recorder ready before Stage 8

You'll need this for the 15–20 minute demo video eventually — install and test it now rather than
during a time-pressured final week:
- **Windows:** built-in Xbox Game Bar (`Win+G`) or OBS Studio.
- **Mac:** built-in `Cmd+Shift+5` screen recording, or OBS Studio.
- **Linux:** OBS Studio, or `SimpleScreenRecorder`.
Do one 30-second test recording now and confirm audio (your narration) is actually being captured —
this is a common last-minute failure that's annoying to discover after a real 20-minute take.

## 8. Final pre-flight checklist before Stage 0 of the Build Guide

- [ ] `java -version` → 17.x
- [ ] `mvn -version` → reports Java 17 as runtime
- [ ] `git --version` → works, identity configured
- [ ] Antigravity installed, signed in, Planning/Fast mode both selectable
- [ ] Terminal Policy set to Auto for standard commands
- [ ] Project folder created, opened as a Workspace (not Playground), architecture/build-guide/spec
      files copied in, git initialized and first commit made
- [ ] Stable internet connection available for the build session
- [ ] Screen recorder installed and test-recorded (can be done later, but don't leave it to the last day)

Once every box is checked, proceed straight to Stage 0 of `Vendor_Engine_Build_Guide.md`.
