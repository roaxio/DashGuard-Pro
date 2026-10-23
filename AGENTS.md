# Meta-Learning & Continuous Improvement

0. **Distill the Principle, Not the Patch (Universal Meta-Learning):**
When resolving a bug, analyzing chat feedback, or codifying a user correction:
   - **Identify the Error Class:** Do not just memorize specific lines of code or local changes (e.g., "change TextButton to Button in Settings"). Ask: *"What universal software engineering principle was violated here, and how do we prevent this entire category of mistakes across any system?"*
   - **The Universal Relevance Test:** Before proposing or saving any new rule, rigorously test whether it is genuinely relevant to *any* possible application, backend service, or project an AI agent might work on. If it contains project-specific or domain-bound trivia, generalize it into a universal systems engineering invariant without losing the core intention behind the lesson.
   - **Autonomous Efficiency (Fewer Prompts):** Internalize root patterns and engineering expectations so that the agent executes with high first-pass precision and autonomy in future sessions, systematically minimizing the need for manual user prompts and corrections.

---

# Error Resolution & Debugging Protocol

When encountering runtime crashes, compiler errors, or system log spam, you MUST adhere to the following architectural debugging rules. **Do not engage in iterative "whack-a-mole" patching.**

1. **Root-Cause Resolution (Treat the Disease, Not the Symptom):**
   * **FORBIDDEN:** Blindly wrapping failing call sites in shallow `try/catch` blocks, forcing null-safe/optional coalescing operators (`?.`, `??`, `unwrap_or_default`), or introducing defensive default values solely to suppress a crash. This silences the alert while allowing corrupted, invalid state to propagate downstream.
   * **REQUIRED:** Analyze the holistic data flow and state machine to determine *why* the system entered an invalid state. Fix the upstream lifecycle, invariant check, or state transition that permitted the illegal state to occur.

2. **Context Fragmentation Gating (Stop and Research on Repeated Failures):**
   * **FORBIDDEN:** Iteratively guessing fixes when an error repeats or immediately triggers a new downstream error, causing context fragmentation and architectural degradation.
   * **REQUIRED:** If an error persists after a **single fix attempt**, immediately halt all code editing. Step back to formulate a technical hypothesis, and use research tools (e.g., web search, official documentation) to verify underlying platform specifications, runtime sandboxing rules, or OS permission constraints before touching code again.

3. **Virtualization & Execution Environment Awareness (No Bare-Metal Assumptions):**
   * **FORBIDDEN:** Assuming that virtualized, containerized, or headless execution environments (e.g., emulators, Docker containers, CI runners, cloud VMs) possess physical bare-metal capabilities (hardware acceleration, GPU encoders, physical sensors, network peripherals).
   * **REQUIRED:** Always design for environmental parity and graceful degradation. Check runtime environment capabilities dynamically (via feature discovery or environment flags) and provide clean fallbacks or mocks rather than writing fragile hacks to force unsupported hardware to execute in sandboxes.

4. **Deterministic Architecture vs. Temporal Duct-Tape (No Heuristic Delays):**
   * **FORBIDDEN:** Stacking arbitrary time delays (`delay()`, `sleep()`, `setTimeout()`, retry counters) or piling on nested boolean flags to paper over asynchronous race conditions, startup ordering bugs, or uncoordinated state transitions.
   * **REQUIRED:** Never use time heuristics to fix race conditions. Delete flawed asynchronous coordination blocks and re-architect them from first principles using deterministic synchronization primitives, formal state machines, or reactive event streams.

---

# Universal Engineering & Architecture Principles

## I. Architectural Foundations, Blueprints & Navigation

5. **Living Architectural Blueprint Invariance & Navigational Cache:**
   * **The Trap:** Treating blueprints (`CODEBASE_MAP.md`, `ARCHITECTURE_FLOW.md`) as static reports, ignoring them to re-scan raw code trees, guessing whether to append code or create new files, or planning code changes without in-phase test runs and blueprint updates.
   * **The Universal Rule:**
     1. **Index-First Navigation:** Query the architectural index first to target candidate files and interfaces; never run broad filesystem sweeps when indexed entities exist.
     2. **Blueprint Evolution (Reuse → Extend → Create):**
        - **Gate 0 (Reality Check):** Ground decisions in actual source code; reconcile blueprint drift before designing.
        - **Gate 1 (Reuse First):** If `CODEBASE_MAP.md` covers the requirement, consume it directly. Bypasses require architectural justification in task planning.
        - **Gate 2 (Extend & Extract):** Extend the owning participant in `ARCHITECTURE_FLOW.md`. Extract private/internal helpers first; never introduce public abstractions for local complexity.
        - **Gate 3 (Create Isolated):** Create a new entity ONLY for genuinely independent lifecycles, concurrency boundaries, or data-pipeline tiers.
     3. **Atomic Phase Cadence (Code → Test → Sync):** 
        Never plan or execute code changes in horizontal isolation. Every phase must explicitly couple three mandatory checkpoints before proceeding to the next:
        - **Code:** Implement following Gates 0–3 as fully wired vertical slices using canonical tags `[path/File.ext::EntityName]`.
        - **Test:** Update/Create Relevant-Tests & Execute empirical verification immediately with hermetic fixture teardown (never defer testing to the end of a milestone).
        - **Sync:** Atomically update `CODEBASE_MAP.md` (entities/invariants) and `ARCHITECTURE_FLOW.md` (flows/sequences), verified via automated tag consistency checks (`validate_audit.py`).

6. **Architectural Cohesion & Dependency Conservatism (Survey Before Synthesis):**
* **The Trap:** Jumping directly into writing code on a new or existing codebase without first surveying existing project structure, patterns, and internal utilities. This leads to duplicate helper modules, fragmented idioms (e.g., three different HTTP/logging approaches in one repo), or pulling in heavy external packages for trivial tasks that the standard library or existing project utilities already solve.
    * **The Universal Rule:** Always enforce discovery before implementation:
      1. **Idiom Discovery:** Survey the existing repository to identify canonical project conventions (error handling patterns, state paradigms, logging wrappers, dependency injection) and strictly adhere to the established style.
      2. **Dependency Conservatism:** Exhaust standard library primitives and existing project dependencies before introducing any external package. Never pull in a third-party dependency for functionality achievable with clean, native functions.
      3. **Single Canonical Utility:** If an internal utility or helper already exists for an operation (path formatting, mathematical conversions, token parsing), reuse or extend it; never create parallel, competing utility functions.
      4. **Consumer Contract Audit & Mediated Topology (Decomposition Hygiene):**
         - Before refactoring or extracting code from complex components, audit downstream callers and test suites for implicit contracts (reflection, property assertions, public API signatures) to preserve 100% behavioral parity.
         - Extracted sibling coordinators must never directly reference or import each other; all cross-domain coordination must be mediated by the owning parent orchestrator via functional callbacks or unidirectional event streams.

7. **Vertical Slice Integrity (Full-Stack Operational Wiring):**
* **The Trap:** Implementing features in shallow, horizontal isolation—e.g., adding a UI button or toggle that only updates a local UI state flag, or writing a backend service that is never triggered by real events. This creates "Potemkin" features: the interface looks complete, but clicking it has no real operational effect on the underlying engine, persistence layer, or hardware pipeline.
    * **The Universal Rule:** Never consider a feature complete until it is implemented as a contiguous, fully wired **vertical slice** across all architectural tiers:
      1. **Surface Tier (UI / API Gateway / CLI):** Accepts user intent and renders reactive state.
      2. **Orchestration Tier (ViewModel / Controller / Domain Service):** Validates intent, manages state transitions, and enforces invariants.
      3. **Engine Tier (Background Services / Workers / Hardware / OS Session):** Executes the actual physical operation, file I/O, or streaming workload.
      4. **Persistence Tier (Database / Storage / Config Store):** Durably commits the state change.
      5. **Reactive Feedback Loop:** The engine's true operational status must flow back up reactively to confirm completion to the UI. If any tier in this chain is a stub, mock, or dead-end, the feature is fundamentally incomplete.

---

## II. Core Systems, Concurrency & State Invariance

8. **Framework State Invariance (Declarative APIs over Low-Level Side Doors):**
* **The Trap:** Bypassing a high-level framework's managed state machine (e.g., ORMs, declarative UI frameworks, runtime session managers, controller loops) by injecting raw, low-level mutations through side doors (e.g., raw SQL queries behind an ORM's back, direct DOM mutations in a virtual DOM framework, raw driver ioctls bypassing the session layer). This desynchronizes the framework's internal reconciliation engine and causes catastrophic state corruption.
   * **The Universal Rule:** Always interact through first-class, declarative framework APIs. If the framework manages the lifecycle and buffer pool, all configuration changes must flow through its official state declarations rather than out-of-band mutations.

9. **Asynchronous Teardown & Lifecycle Symmetry (Preventing Startup Purgatory):**
* **The Trap:** Designing asynchronous start/stop pipelines where cancel or stop commands issued while an operation is still starting are ignored or dropped, leaving the resource in an un-abortable "starting" purgatory that permanently blocks future operations.
   * **The Universal Rule:** Every asynchronous lifecycle must be strictly symmetric. If a teardown or abort command arrives before startup or initialization is fully confirmed, immediately abort the pending resource allocation and reset all state flags (`isStarting`, `isPending`, `isStopping`) to a clean idle state.
   * **Standby Override Isolation & Symmetrical Recovery Hygiene:**
     When a system executes a discrete emergency task or high-priority override while in a suspended, cooling, or low-power standby state:
     1. **Scoped Override:** Restrict elevated flags (locks, emergency tags, high-power modes) strictly to that discrete task.
     2. **Symmetrical State Hygiene:** Upon transitioning from standby/sleep back to primary operational duty, explicitly sanitize and purge all transient override flags to guarantee the resumed primary workflow begins with a completely clean, uncorrupted state.

10. **System Invariant Formulation & Lifecycle Enforcement (Admission, Execution, Eviction):**
* **The Trap:** Either formulating a system constraint against dynamically shrinking telemetry (causing negative feedback collapse where consumption artificially shrinks the allowed quota), enforcing the constraint in only a single execution phase (such as a periodic cron) while allowing jobs to initiate blindly in-between, or hardcoding rigid scalar capacity limits (e.g., fixed file size quotas, fixed memory buffer sizes, static thread counts) that starve low-end hardware and underutilize high-end systems.
    * **The Universal Rule:**
      1. **Stable Formulation:** Always compute capacity against a stable invariant: `Total Available Pool = Current Free Space + Space Currently Allocated By System`.
      2. **Dynamic Proportional Capacity (Zero Hardcoded Resource Budgets):**
         - Never hardcode static scalar quotas for disk retention, cache capacities, buffer pools, or worker concurrency.
         - Derive operational limits dynamically from runtime environment capacity (`totalStorage`, `maxMemory()`, available processor cores) using bounded clamped scaling:
           `budget = clamp(minOperationalFloor, totalPool * targetProportionalRatio, maxSafeCeiling)`.
         - Resource allocation must adapt gracefully across heterogeneous environments without environment-specific branching.
         - **Dynamic Environmental & Safety Thresholds:** Never hardcode rigid scalar cutoffs for warning triggers, hysteretic recovery targets, or protective auto-demotions. Derive all operational safety boundaries reactively from authoritative user settings or hardware telemetry using calibrated offsets (e.g., `warningThreshold = max(floor, userLimit - deltaWarning)`, `recoveryTarget = userLimit - deltaHysteresis`).
      3. **Three-Gate Lifecycle:** Enforce the invariant symmetrically across all three checkpoints:
         - **Admission Control (Pre-Flight):** Verify capacity *before* initiating any job or task.
         - **Execution Monitoring (In-Flight):** Detect rapid resource exhaustion mid-stream to throttle or safely abort before fatal breaches.
         - **Eviction / Reconciliation (Post-Flight):** Purge resources *only* when the invariant is actually breached, using the identical configured thresholds without ad-hoc secondary magic numbers.

11. **Domain Metric Authority & Representation Orthogonality (Single Source of Domain Truth):**
* **The Trap:** Computing the same domain metric (storage used, balances, active sessions) through disparate ad-hoc queries across different screens or API endpoints, or using human-facing presentation strings (rounded values, localized currencies, formatted dates) as inputs to business logic and threshold checks.
    * **The Universal Rule:**
      1. **Single Source of Truth:** Centralize domain metric calculation in a single authoritative domain service or repository that accounts for all companion data, sidecars, and metadata. Every UI surface, API response, and CLI command must query this single authority.
      2. **One-Way Presentation Boundary:** Internal state machines and calculations must operate exclusively on raw, lossless primitives (raw integer bytes/cents, canonical epoch timestamps). Human-facing formatting is strictly a terminal, one-way leaf operation for rendering.

12. **Stateful Mutex & Configuration Guardrails (Protecting In-Flight Work):**
* **The Trap:** Permitting environmental reconfigurations (e.g., switching database connections, changing file storage targets, swapping active network interfaces, altering system modes) while stateful, long-running operations are actively executing, causing torn writes, dropped packets, or crashes.
    * **The Universal Rule:** Guard stateful workers with a configuration mutex:
      1. **Command Mutex:** When a reconfiguration command is issued while a process is in-flight, never mutate state out from under the executing worker. Provide a frictionless, explicit protocol: either queue the change until completion, or present an explicit interruption guardrail ("Work in progress: Abort and switch?") that cleanly tears down the active pipeline before applying the change.
      2. **Environmental Sensor Mutation Mutex:** If an external sensor or OS event threatens to trigger an environmental reconfiguration (e.g., screen orientation flip, window resize) during an active hardware capture or streaming session, dynamically pin and freeze the runtime configuration to protect in-flight I/O pipelines from surface recreation and frame drops. Symmetrically release the lock upon session termination.
      3. **Three-Phase Lifecycle Guardrails (Clean Entry, Protected Execution, Clean Exit):**
         - **The Trap:** Permitting transient startup noise, uncalibrated sensor signals, or lingering override flags to corrupt operational state on entry; allowing external environmental transitions (power loss, low-battery cutoffs, sleep commands) to abort high-priority in-flight workloads mid-stream; or allowing post-event decay oscillations and dirty flags to trigger recursive loops or corrupt resumed modes on exit.
         - **The Universal Rule:** Guard sensitive stateful lifecycles across all three operational boundaries:
           - **Clean Entry (Ingress Settling & Baseline Sanitization):** Discard transient startup samples, reset temporal comparison buffers, and purge any prior transient override flags upon sensor, driver, or mode activation before admitting data to detection thresholds. If an emergency action is triggered from an idle state, affirmatively spin up the execution engine for the full safety duration rather than merely setting a passive state flag.
           - **Protected Execution (Workload Shielding & Deferred Transitions):** Physically shield active, high-priority stateful tasks from mid-stream cancellation. Record and queue incoming environmental or lifecycle commands (`PendingPostAction`), guaranteeing the required safety buffer duration completes durably before safely executing the deferred transition.
           - **Clean Exit (Cooldown Hysteresis & Flag Quarantine):** Enforce an explicit quiet cooldown period after task finalization before re-arming detection triggers (preventing decay transients or feedback echoes from inducing recursive loops), and strictly sanitize and quarantine task-scoped flags from leaking into subsequent operational baselines.

---

## III. Data Pipelines, Ingress & Platform Bridges

13. **Three-Tier Pipeline Isolation (Eliminate Symptom-Guessing):**
* **The Trap:** Applying speculative retry loops or ad-hoc patches to symptoms at the end of a complex data, messaging, or streaming pipeline without knowing which stage failed.
   * **The Universal Rule:** Systematically map and isolate failures across three distinct architectural boundaries:
     1. **Producer (Ingress):** Is the source generating valid payloads, correct formats, and non-null timestamps?
     2. **Pipeline / Broker (Transport & Lifecycle):** Is the session binding valid, the queue unclogged, and backpressure respected?
     3. **Consumer (Egress & Storage):** Is the output sink, serializer, or storage container properly configured for format compatibility and throughput?

14. **Lossless Pass-Through & Serialization Fidelity (Avoid Lossy Defaults):**
* **The Trap:** Relying on standard library or third-party framework defaults when proxying, transforming, or serializing data streams, which frequently apply lossy downsampling, silent floating-point truncation, or web-optimized compressions that destroy the source data's intrinsic precision.
   * **The Universal Rule:** In any pass-through, transformation, or proxy pipeline, explicitly inspect and preserve the source asset's intrinsic properties (bitrate, numerical precision, color/coordinate space, timezone offsets). Never assume framework defaults match your source fidelity.

15. **Idempotent State Relocation & Federated Scope Discovery (Safe Partition Transitions):**
* **The Trap:** When a system allows users or administrators to change active storage targets, tenants, or partitions: (1) narrowing the query scope strictly to the new target, turning historical data into invisible "ghost state", and (2) performing naive data transfers that crash, overwrite data, or miscalculate transfer requirements when items already exist at the destination.
    * **The Universal Rule:**
      1. **Federated Discovery (Read Path):** Decouple the *active write target* from the *global read index*. Aggregation and management components must index and federate data across all known historical and active partitions, attributing origins transparently until the user or system explicitly triggers a consolidation or purge.
      2. **Idempotent Relocation (Write Path):** Any transfer pipeline must be idempotent and destination-aware. Verify canonical identities (`sourceCanonical == destinationCanonical`) so self-moves resolve as instant, zero-cost successes. Exclude items already at the destination from progress meters and capacity pre-flight calculations.

16. **Reactive Hardware Ingress & Upstream Pipeline Decoupling (Edge Thermal & Resource Preservation):**
    * **The Trap:** Designing mobile or edge systems that exhaust hardware resources and trigger thermal throttling through four common anti-patterns:
      - Running background polling loops (`while(true) { delay(...) }`, `ScheduledExecutorService`) to check system or device state, preventing CPU cores from entering deep sleep states.
      - Masking or obscuring high-power rendering surfaces (camera previews, 3D canvases, video layers) with black overlays while leaving upstream hardware capture and GPU compositing pipelines running at full throttle underneath.
      - Directly piping high-frequency hardware sensor streams (e.g., native IMU or accelerometer sampling) into reactive UI state, swamping UI threads and reconciliation engines with redundant recompositions.
      - Relying on speculative runtime downloads or external file paths for core feedback assets instead of embedding them directly into compile-time package resources.
    * **The Universal Rule:** Enforce strict resource and thermal conservation across four architectural tiers:
      1. **Push over Poll (Zero-Overhead Idle):**
         - Never poll system metrics, battery levels, temperature, or peripheral connections in periodic loops.
         - Bind exclusively to platform/OS event broadcasts or reactive push streams (`ACTION_BATTERY_CHANGED`, hardware interrupts, file watchers).
         - Enforce deadband hysteresis (a calibrated delta or percentage margin to rearm) on threshold-triggered actions to eliminate rapid bouncing across boundary values.
      2. **Upstream Pipeline Teardown (Source Teardown over Surface Masking):**
         - When a view surface is obscured, blacked out, or dimmed, never merely paint black pixels over it.
         - Actively disconnect the rendering sink (detach CameraX surface providers, pause render clocks, disable layer buffers) to stop GPU shaders and window buffer swapping at the source, while keeping independent background capture/recording pipelines active.
         - Reinstate surface providers instantaneously upon user un-dimming or surface visibility restoration.
      3. **Frequency-Tier Decoupling (Safety Ingest vs. Presentation Ingress):**
         - Strictly decouple internal safety/detection algorithms from presentation dispatches.
         - Critical real-time monitoring (e.g., crash detection, high-rate anomaly scanning) executes at native hardware sampling rates in background threads.
         - Telemetry UI flows must be throttled to human-perceptible rates dynamically synchronized to the display refresh cadence (VSYNC / frame ticks) or significant delta thresholds, eliminating redundant UI wakeups, thread contention, and recomposition churn.
      4. **Compile-Time Asset Self-Containment (Zero-Download Offline Invariant):**
         - All operational audio alerts, icons, and fallback media must be bundled directly into compile-time package resources (e.g., `res/raw/`).
         - Verify asset inclusion in the compiled build artifact (`.apk`, binary bundle) and ensure guaranteed offline playback without cloud calls or file system assumptions.

---

## IV. Security, Privacy & Resource Reality

17. **Operational Reality & Zero Speculative Overhead (No Zombie Controls or Phantom Infrastructure):**
* **The Trap:** Tolerating speculative drift across the system: either scaffolding idle background workers, polling loops, or event listeners that run continuously without real production consumers (wasting CPU/memory and causing phantom logs), or exposing non-viable, untested, or broken controls (placebo UI toggles, dead CLI flags) with aspirational descriptions.
   * **The Universal Rule:** Interfaces and execution engines must strictly mirror reality:
     1. **Prune Zombie Interfaces:** If a feature or fallback cannot function reliably in real-world conditions, ruthlessly remove it from public interfaces rather than offering a placebo toggle. Documentation and UI text must rigorously describe what the engine actually executes.
     2. **Eliminate Phantom Infrastructure:** Keep active execution trees ruthlessly minimal. Never spin up background workers or listeners until their complete end-to-end trigger lifecycle and production consumers are implemented and verified.

18. **Principle of Least Privilege & Perimeter Sandboxing (Component & Egress Isolation):**
* **The Trap:** Either granting broad, blanket permissions (e.g., adding unnecessary `INTERNET` permissions to a purely offline app, requesting global storage access instead of scoped storage), or assuming that because an app is "mostly offline", it's fine to leave internal services, activities, or IPC endpoints publicly exported and unauthenticated.
    * **The Universal Rule:** Enforce strict architectural sandboxing and a default-deny posture across two distinct operational modes:
      1. **The Offline / Local-First Baseline (Pure Air-Gap):**
         - If an application or module is designed for local execution (e.g., local dashcam recording, offline utilities, local database tools), the network posture is a **strict zero-permission air-gap**.
         - **Zero-Network Invariant:** Never request or declare network permissions (e.g., no `<uses-permission android:name="android.permission.INTERNET"/>`).
         - **Zero Speculative Adapters:** Never scaffold mock HTTP clients, sync workers, or network adapters until an explicit cloud feature is officially planned and requested (enforcing Rule 17). Local hardware streams (camera, mic, GPS) must terminate exclusively in local persistence/encoders.
      2. **The Connected / Hybrid Baseline (Perimeter Egress Gating):**
         - If and when network communication is explicitly required, isolate all socket/HTTP traffic to a single, dedicated network adapter module. Core processing and storage engines must remain network-agnostic.
      3. **Default-Deny Component Exposure:**
         - All internal services, activities, broadcast receivers, and IPC endpoints must be explicitly non-exported (`exported="false"`) by default. Never expose internal components to other apps or the OS without explicit signature-level permissions.
      4. **Scoped Capability Gating:**
         - Request the minimum necessary hardware and OS access (e.g., Scoped Storage / Storage Access Framework instead of raw external storage, coarse location when high-precision positioning is not required). Check permissions just-in-time and provide graceful, non-crashing fallbacks if denied.

19. **Data Minimization & Sensitive Telemetry Hygiene (Zero-Leak Privacy & Local Keystores):**
* **The Trap:** Assuming that because an app is offline or running locally, debug logs (`Log.d`, `console.log`, stdout) and local files are safe to contain raw PII, sensitive coordinates, speed telemetry, or encryption keys. System log buffers (Logcat, syslog) and crash dumps are shared OS resources accessible via ADB or bug reports, creating severe privacy leaks.
    * **The Universal Rule:** Enforce strict cryptographic secrecy and telemetry hygiene regardless of connectivity:
      1. **Local Logcat & Telemetry Sanitization:**
         - Treat all system loggers and crash reporters as untrusted, semi-public sinks.
         - Never emit raw user personal data (PII), exact GPS coordinates, vehicle speeds, auth headers, or private keys into `Log.d`, `println`, or crash reports.
         - Redact, hash, or tokenize telemetry before emission, or gate diagnostic logging behind explicit, non-production build flags (`BuildConfig.DEBUG`).
      2. **Hardware-Backed Local Cryptography (Zero-Cloud Dependency):**
         - When encrypting sensitive assets, video locks, or credentials locally, use platform hardware-backed keystores (e.g., `AndroidKeyStore`, Apple Secure Enclave, local OS keyring) that operate strictly offline without requiring cloud KMS or external network dependencies.
         - Never store raw symmetric keys or passwords in plaintext files, code constants, or shared preferences.
      3. **Ephemeral Retention & Memory Wiping:**
         - Retain sensitive data (temporary video frame buffers, raw sensor arrays, decrypted secrets) only for the minimal operational window.
         - Overwrite or release sensitive memory buffers immediately after processing to prevent lingering data in memory dumps.

---

## V. Declarative UI Architecture & Human Ergonomics

20. **UI Framework Slot Integrity:**
* **The Trap:** UI frameworks (Compose, React, Flutter) often provide built-in components (like Dialogs) with predefined "slots" (e.g., a slot for a Confirm button, a slot for a Cancel button). When developers try to fit complex, custom layouts into these predefined slots, the framework's internal layout engine gets confused, causing overlapping buttons and broken UI.
    * **The Universal Rule:** Never fight the framework's default slots. If you need a custom multi-action layout (such as custom actions with specific spacing or arrangements), disable/nullify the framework's secondary slots. Inject your entire custom layout into the *single* primary slot. This gives you absolute, deterministic control over the rendering hierarchy.

21. **Information Density & Cognitive Load (HUD Layouts):**
* **The Trap:** In glanceable interfaces (dashboards, camera HUDs, smartwatches), scattering data points (battery in one corner, temperature in another, status text somewhere else) forces the user's eyes to dart around, increasing cognitive load and risking misclicks on the actual interactive buttons.
    * **The Universal Rule:** Consolidate related secondary telemetry (status, battery, sensors) into a single, cohesive, inline stream (like a unified top bar). This frees up the center of the screen for primary interactive elements (such as the main focal action) and makes the UI significantly safer and faster to read.

22. **Fluid Viewport Invariance, Pragmatic Ergonomics & Modern Aesthetics (Zero Hardcoded Magic Numbers):**
   * **FORBIDDEN:** Hardcoding arbitrary scalar constants for structural dimensions, margins, padding, or font sizes (e.g., `.width(240.dp)`, `.height(180.dp)`, `w-[300px]`, `Spacer(Modifier.height(48.dp))`, static `32.sp`/`28px`), or rendering flat, unstyled default containers.
   * **REQUIRED (Pre-Commit UI Audit Gate):** Before running tests on any UI edit, verify every modified component satisfies these 8 atomic invariants (refactor any rigid scalar container constant immediately):
     1. **Fluid Proportional Geometry:** Derive all structural sizes (screens, cards, docks, modals) reactively from runtime viewport bounds, aspect ratio, and orientation (`BoxWithConstraints`, `.weight(1f)`, `.fillMaxWidth(fraction)`, `aspectRatio()`, CSS `clamp()`/`fr`) so layouts adapt from narrow phones to wide automotive/tablet displays without screen-specific branching.
     2. **Clamped Fluid Typography & Tabular Telemetry:** Scale fonts dynamically from viewport geometry (`fontSize = clamp(minSp, viewportWidth * scaleFactor, maxSp)`), prevent awkward multi-line wrapping in height-constrained containers (`maxLines = 1`, `TextOverflow.Ellipsis`), reclaim bloated framework default padding, and enforce `tabular-nums` (`tnum`) on all live-updating metrics so digits never jitter horizontally.
     3. **Adaptive Proportional Spacing ("The Gap" Invariant):** Derive margins, internal padding, and sibling gaps proportionally from active screen geometry rather than static magic numbers, and maintain distinct proportional separation between opposing or destructive sibling actions (e.g., Confirm vs. Delete) to prevent thumb misclicks.
     4. **Touch Targets as Dynamic Floor:** Treat platform accessibility touch bounds (`48.dp`/`48px`) strictly as a `minSize` **floor**, never a rigid ceiling; allow interactive containers to expand fluidly to fill their proportional layout budget with centered icons/text, keeping primary focal actions dominant in thumb reach.
     5. **Glanceable & Dimmed Surface Disambiguation:** On low-power, ambient, or dimmed stealth surfaces where users expect a non-destructive tap-to-wake, never place a mutating action in the central blind-tap landing zone, and ensure instructional copy strictly matches reality (never state *"Tap anywhere to wake"* if a region triggers an action).
     6. **Explicit Interactive Affordance & Modern Surface Depth:**
        - **Action Hierarchy:** Interactive elements must never look like passive labels; use solid high-contrast fills for **Primary** actions, `1dp`/`1px` visible borders or tonal fills for **Secondary** actions, and defined hit containers for **Tertiary** actions, all with immediate press/state-transition feedback.
        - **3-Tier Surface Depth & Contrast:** Differentiate **Base Canvas** (deep neutral background), **Elevated Containers/Cards** (subtle tonal luminance shift + `1dp`/`1px` border at `10%–16%` contrast alpha), and **Interactive Controls**, establishing hierarchy via weight and opacity contrast (**Primary:** Bold `100%` alpha; **Secondary:** Medium `70%` alpha; **Labels/Units:** Tracked/Monospace `50%` alpha).
     7. **Harmonic Single-Plane Axis Balancing:** Parallel docks, toolbars, and vertical control rails must share balanced gravity and centering across columns; never nest sibling controls into an ad-hoc sub-column inside `SpaceBetween`—flatten siblings onto a single layout plane with uniform spacing (`SpaceEvenly` + equal weights).
     8. **Complete State Quintet & Unclipped Content Resilience:**
        - Gracefully render all 5 operational states: **Default/Populated**, **Feedback/Active**, **Loading** (non-blocking skeletons), **Empty** (clear copy + actionable CTA), and **Error** (plain-language cause + recovery escape hatch).
        - Never permanently clip real-world domain paths or localized strings into unreadable ellipses without an expansion mechanism (tap-to-expand, marquee, tooltip, or sheet), and tuck granular options into sub-screens without duplicating parent controls.
        
---

## VI. Verification, Testing & Environment Hygiene

23. **Empirical Verification & Closed-Loop Validation (The "No Unverified Victory" Rule):**
* **The Trap:** Declaring an implementation, bugfix, or refactor complete based solely on theoretical correctness, speculative static analysis, or visual code inspection without running automated verification. This offloads compilation checks, runtime sanity testing, and regression discovery onto the user, causing immediate frustration when the project fails to build or pass tests.
    * **The Universal Rule:** Never declare completion, invite user review, or mark a task finished without empirical, closed-loop validation:
      1. **Build & Execution Invariance:** Actively execute the project's build tool, compiler, and automated test suite locally to verify zero build errors.
      2. **Differential Regression Coverage:** Whenever resolving a bug or adding logic, write or execute automated tests that explicitly exercise the edge case (proving that the failure is prevented and no adjacent regressions exist).
      3. **Clean-Log Discipline:** Inspect build logs and test outputs to ensure no new warnings, deprecations, or uncaught background exceptions were introduced by the change.

24. **Hermetic Environment Invariance & Symmetrical Fixture Teardown:**
* **The Trap:** Altering runtime environment fixtures (mocking sensor streams, injecting GPS coordinates, faking AC power states, overriding network interfaces, mutating system clocks) during a test or diagnostic run, and leaving those mutations active. Subsequent tests inherit a corrupted, non-baseline environment, producing phantom bugs, misleading results, and hours of wasted debugging.
    * **The Universal Rule:**
      1. **Strict Setup/Teardown Symmetry (RAII Fixtures):** Every synthetic environment override or mock must be paired with an unconditional teardown routine.
      2. **Pre-Flight Baseline Invariant:** Before initiating any test or simulation, the agent must query and assert that the execution environment is in a known canonical baseline (e.g., zero artificial speed, standard charging, neutral orientation).
      3. **Post-Flight Force Clean:** Whether a test succeeds, fails, or crashes mid-stream, the teardown hook must force-reset all altered system variables back to baseline defaults before any subsequent step begins.

---

## VII. Autonomous Agent Orchestration & Collaboration

25. **Optimal Human-in-the-Loop Partitioning (Friction-Aware Delegation):**
* **The Trap:** An autonomous agent stubbornly attempting low-accuracy, high-latency physical or visual interactions (guessing screen pixel coordinates for clicks, wrestling with complex interactive modals, or attempting to trigger hardware-dependent states in a sandbox) while a human operator is actively observing and offering assistance. This inflates task latency from seconds to dozens of minutes and causes immense user friction.
    * **The Universal Rule:**
      1. **Task Asymmetry Recognition:** Divide tasks dynamically by operational efficiency:
         - **Agent Domain (High Precision, Zero Friction):** Log parsing, AST analysis, automated compilation, script execution, database queries, and deterministic assertion checking.
         - **Human Domain (Instant Intuition, Zero Guesswork):** Visual disambiguation, complex spatial tapping, physical hardware cord adjustments, and biometric/OS permission taps.
      2. **Proactive Friction-Gated Delegation:** When an operation requires ambiguous UI screen coordinate tapping or physical interaction, provide the exact intent and ask the user to execute the physical action ("Please tap the Settings gear on screen") while the agent concurrently monitors logs, streams, and state transitions.

26. **Timeboxed Autonomous Cadence, State Anchoring & Transcript Grounding:**
* **The Trap:**
      1. Disappearing into a long-running autonomous execution loop ("the black hole") for 20–30+ minutes without emitting progress updates, status heartbeats, or checkpoint summaries.
      2. Relying on conversational short-term memory or rolling summary horizons when recovering from context truncation, suffering from recency bias and losing track of earlier foundational tasks.
    * **The Universal Rule:**
      1. **Cadence Heartbeats (No Silent Deep Dives):** When executing an autonomous multi-step workflow, verification run, or test suite, the agent must emit a concise milestone update at reasonable intervals (or every major sub-phase).
      2. **Timebox Gating:** If a single test or diagnostic task exceeds 3–5 minutes due to retries or logging loops, halt the silent loop, state the blocker/hypothesis, and calibrate with the user or pivot to the next structured step.
      3. **Durable State Anchoring:** Multi-step work must always be anchored in a persistent tracking artifact (`TEST_PLAN.md` or `implementation_plan.md`). Conversational interruptions must never displace the authoritative status of the active plan.
      4. **Forensic Transcript Grounding:** When asked to review, audit, or summarize historical session progression in a truncated context window, the agent must inspect persistent transcript artifacts (`transcript.jsonl`) to reconstruct the true chronological baseline rather than hallucinating from compacted recency.
