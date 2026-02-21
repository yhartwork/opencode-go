# OpenCode Mobile Client Rewrite Blueprint

## 1) Product North Star

Build a reliable, mobile-native OpenCode client that feels like a serious engineering tool, not a demo chat app.

Success means:
- sessions are easy to create, find, and continue;
- streaming responses are understandable while they happen;
- tool activity, errors, and diffs are visible and actionable;
- network instability is handled explicitly (not hidden);
- model/provider context is always clear.

This document is grounded in:
- current Android app in this repo;
- OpenCode API surface used in app;
- behavior patterns from `packages/desktop` and `@opencode-ai/app` (primary parity target);
- selective timeline rendering ideas from web share views.

## Decision Update (Reference + Runtime Model)

- Parity target is `packages/desktop`, not the marketing/docs web package.
- Mobile app is `remote-client-only`: no local sidecar startup.
- User provides a base URL to an already running OpenCode instance.
- App startup checks saved base URL and health, then routes to Chats or Connection.

## 2) Current App Gaps (Why It Feels Unusable)

### UX/IA gaps
- Single-screen, drawer-heavy flow does not scale for frequent session switching.
- Chat is rendered as plain bubbles only; OpenCode parts (tool actions, step boundaries, errors, patch-like content) are collapsed into text or dropped.
- Model/provider context exists but lacks confidence UX (capabilities, defaults, fallback behavior, per-session pinning).
- Session list metadata is weak (minimal preview, no status chips, weak selection and action affordances).

### Reliability gaps
- Limited explicit connection-state handling (connecting/reconnecting/error semantics are not consistent across screen state).
- Weak retry/recovery mechanics for transient failures.
- No local data cache strategy for fast resume/offline read.

### Architecture gaps
- Activity-centric orchestration with broad mutable state.
- Network + parsing + UI concerns tightly coupled.
- JSON parsing is manually scattered and difficult to evolve safely.

## 3) Functional Baseline To Match (Desktop-Informed)

Desktop app behavior and app-core workflow are the primary baseline. Timeline rendering remains part-aware like web share.

Baseline features for parity:
- Connection setup and health validation.
- Session list and CRUD basics.
- Message history and streaming response updates.
- Provider/model selection.
- Part-aware rendering for key message part types.
- Visible connection state and graceful reconnect behavior.
- Lightweight cost/token/model metadata in-session.

## 4) Mobile Information Architecture

## App Shell (bottom navigation)
- `Chats`: session list + active chat entry point.
- `Workspace`: project/worktree context, recent file operations, lightweight repo indicators.
- `Models`: provider/model browse, capability badges, defaults.
- `Settings`: server profiles, diagnostics, logging, appearance, advanced toggles.

Global action:
- `New Session` primary action from Chats and Chat header.

## 5) Screen-by-Screen Product Spec

## A. Connection + Onboarding

Purpose: get user to first successful interaction quickly and safely.

Required behavior:
- Support at least one user-provided base URL in V1 (multi-profile can follow).
- Validate URL, run health check, fetch version, and show clear status.
- Store last successful profile and auto-reconnect on cold start.
- Provide diagnostics panel (health endpoint result, latency, error body snippet).

States:
- idle, validating, success, auth_required (future), unreachable, invalid_url.

## B. Session List (Chats Home)

Purpose: fast re-entry into active work.

Each row should include:
- title/slug fallback;
- updated timestamp;
- model/provider chip (if known);
- short preview from latest user or assistant text;
- change summary chip (`+/- files`) when available;
- status marker (streaming/error/synced).

Actions:
- tap: open chat;
- swipe: archive/delete (confirm destructive);
- long-press: rename/share/fork/delete;
- pull-to-refresh and optimistic inline creation.

## C. Chat Detail

Purpose: high-fidelity, understandable live work stream.

Header:
- session title;
- connection badge (connected/reconnecting/error);
- model chip; quick model switch;
- overflow: rename/share/delete/fork.

Timeline renderer (core requirement):
- User text blocks.
- Assistant text blocks (streaming cursor state).
- Reasoning blocks (collapsed by default, expandable).
- Tool invocation/result cards with status (`pending/running/completed/error`).
- Error cards with copy + retry affordance.
- Diff/summary cards for code changes.

Composer:
- multiline input;
- send/stop controls (stop during stream);
- disabled semantics while no active session.

Footer metadata strip:
- cost, input/output/reasoning tokens, provider/model, latest event timestamp.

## D. Models

Purpose: transparency and control.

Features:
- Group by provider;
- capability icons (reasoning, toolcall, attachment, modalities);
- default model setting (global);
- optional per-session override;
- indicate disconnected providers.

## E. Workspace

Purpose: project context anchoring.

V1 scope:
- show current project + directory/worktree;
- recent session-associated files (if available from summaries);
- open in external editor action (future deep links).

## F. Settings

V1:
- server profiles and default profile;
- reconnect/backoff toggles (advanced);
- diagnostics export (logs + endpoint snapshots);
- app theme choice (system/light/dark) and text density.

## 6) Data Model + API Contract Strategy

Adopt a strict DTO -> domain mapper approach.

## Core domain entities
- `ServerProfile`
- `SessionListItem`
- `SessionDetail`
- `TimelineEvent` (sealed hierarchy)
- `ModelCatalog`, `Provider`
- `ConnectionState`

## TimelineEvent sealed model (critical)
- `UserText`
- `AssistantText`
- `Reasoning`
- `ToolCall`
- `ToolResult`
- `PatchSummary`
- `ErrorEvent`
- `SystemEvent`

The reducer should consume snapshot API responses and streaming events into one normalized timeline.

## 7) Streaming + Connection State Machine

Runtime model:
- No `ServerGate` for sidecar boot.
- Use a connection bootstrap check on launch (`saved URL -> /global/health -> route`).

Single, explicit state machine:
- `Disconnected`
- `Connecting`
- `Connected`
- `Reconnecting(attempt, nextDelayMs)`
- `Error(message)`

Rules:
- On socket/SSE drop: transition to `Reconnecting` with exponential backoff + jitter.
- Keep UI interactive when possible; queue send attempts only when safe.
- Never silently fail; surface transient vs terminal errors separately.

## 8) Recommended Technical Stack (Android)

- UI: Jetpack Compose + Material 3.
- State: MVI-style unidirectional flow with `StateFlow`.
- DI: Hilt/Koin (team preference).
- Network: Retrofit + OkHttp + kotlinx serialization (or Ktor).
- Persistence: Room (sessions/messages cache) + DataStore (preferences/profiles).
- Background: WorkManager for sync/cleanup tasks.

Project module suggestion:
- `app` (composition root)
- `core/designsystem`
- `core/network`
- `core/database`
- `feature/connection`
- `feature/chats`
- `feature/chatdetail`
- `feature/models`
- `feature/settings`

## 9) UI System Direction

Visual intent: "technical console, mobile-native".

Guidelines:
- Use design tokens for color, spacing, typography, elevation.
- Use semantic colors for status; avoid hardcoded legacy palette values.
- Distinguish plain text vs technical output with typography and container style.
- Keep progressive disclosure for dense blocks (tool output, stack traces, diffs).
- Prioritize one-thumb interaction for key actions.

## 10) Performance + Reliability Targets

- Cold start to session list: < 2.0s on mid-tier device.
- First visible token after send (good network): < 1.5s median.
- Reconnect recovery after transient loss: < 3s median.
- Chat screen stable at 1k+ timeline events (virtualized/lazy list).
- Zero silent send failures.

## 11) Security + Privacy Baseline

- Profile-scoped server URLs; never log secrets.
- Redact sensitive payloads in exported diagnostics.
- Clear local app data option from settings.
- Clear user-facing copy on what is stored locally.

## 12) Analytics and Diagnostics

Track product-health events:
- connection success/fail by cause;
- send success/fail;
- reconnect count and duration;
- render errors by part type;
- session load latency.

Diagnostics screen should expose:
- current profile;
- last health check timestamp;
- API and stream status;
- recent non-fatal errors.

## 13) Delivery Roadmap (Phased Rewrite)

## Phase 0 - Foundation (1 sprint)
- Compose app shell + navigation scaffold.
- Design tokens + theme system.
- Network layer and DTO contracts.
- Base URL storage and onboarding flow.

Exit criteria:
- connect to server, pass health check, persist profile.

## Phase 1 - Chats Core (1-2 sprints)
- Session list with CRUD.
- Chat detail with text streaming.
- Model/provider picker + persisted defaults.

Exit criteria:
- create session, send prompt, receive streamed assistant text reliably.

## Phase 2 - Structured Timeline (1-2 sprints)
- Tool/reasoning/error/diff event rendering.
- Unified reducer from history + stream.
- Retry and failure affordances.

Exit criteria:
- major part types render correctly and remain understandable.

## Phase 3 - Reliability + Workspace (1 sprint)
- Cache/resume behavior.
- Workspace context screen.
- Diagnostics export and stability hardening.

Exit criteria:
- app remains functional under intermittent network and resume scenarios.

## 14) Acceptance Checklist (V1)

- User can manage multiple server profiles.
- User can create, switch, and delete sessions.
- User can send prompts and view streaming assistant output.
- User sees model/provider context and can change model.
- User sees tool activity and errors as structured timeline blocks.
- User sees connection state changes and can recover from failures.
- User can reopen app and continue recent sessions quickly.

## 15) Immediate Next Implementation Tasks in This Repo

1. Migrate from XML Activities to Compose scaffold while preserving existing API calls as transitional adapters.
2. Introduce `ConnectionState` and `TimelineEvent` domain models before UI rewrite to prevent another monolithic activity.
3. Replace `OpenCodeClient` manual parsing with DTO mapper layer incrementally endpoint-by-endpoint.
4. Build `feature/connection` and `feature/chats` first; keep `Workspace` minimal until timeline renderer is stable.
5. Keep startup path remote-only (`saved URL + health`) and avoid local server orchestration complexity.

## 16) Implementation Status (Current Repo)

Completed now:
- Compose app shell with tab navigation is in place.
- Startup routing uses remote connection bootstrap (`saved base URL -> /global/health`).
- Connection settings screen supports URL edit, health test, and disconnect.
- Session list screen is wired to real `OpenCodeClient.listSessions()` using saved base URL.

Next in queue:
- Replace temporary session preview/model placeholders with real latest message + model metadata.
- Implement create/delete session actions in Chats.
- Add Chat Detail screen with streaming timeline (`TimelineEvent` reducer + SSE integration).
