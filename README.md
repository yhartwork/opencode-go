# Android Kotlin (Code-Only Repo)

This repository contains a native Android Kotlin app that can talk to OpenCode instances over HTTP.

Local Android SDK/compiler setup is not required for repository CI. APK build happens in GitHub Actions.

## App features

- Load OpenAPI spec from `<base-url>/openapi.json`
- Show supported endpoint list from the spec
- Save and reuse multiple OpenCode base URL profiles
- Load providers/models from `GET /provider` and choose one for chat
- Create a session via `POST /session`
- Stream replies token-by-token using:
  - `POST /session/{sessionID}/prompt_async`
  - `GET /event` (SSE, reads `message.part.delta`)

## OpenCode endpoint coverage noted from spec

Checked against `https://opencode.ai/openapi.json`.

- Total paths discovered: 82
- Core chat/session endpoints:
  - `GET,POST /session`
  - `GET,POST /session/{sessionID}/message`
  - `POST /session/{sessionID}/prompt_async`
  - `POST /session/{sessionID}/init`
  - `POST /session/{sessionID}/abort`
  - `GET /session/{sessionID}/children`
  - `POST /session/{sessionID}/fork`
  - `POST /session/{sessionID}/summarize`
  - `POST /session/{sessionID}/revert`
  - `POST /session/{sessionID}/unrevert`
- Additional groups in spec include: global, config, provider, project, mcp, pty, file/find, permission, tui, experimental, vcs.

## CI

Workflow file: `.github/workflows/android-ci.yml`

On every push and pull request, CI will:

1. Set up JDK 17
2. Install Android SDK packages
3. Run `./gradlew --no-daemon assembleDebug`
4. Upload `app-debug.apk` as an artifact
