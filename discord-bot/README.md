# BIT Systems Engine - Discord Edge Service

High-performance, serverless edge runtime for Discord interactions hosted 24/7 on Cloudflare Workers.

---

## Live Deployment Status
- **Status:** 🟢 24/7 Operational (Cloudflare Global Edge)
- **Endpoint URL:** `https://bit-discord-bot.2403031460945.workers.dev`
- **Application ID:** `1560841802590724136`
- **Target Guild:** BIT Community Server (`1539629617508384849`)

---

## Interactive Feature Matrix

### 1. Action Buttons (`/download`)
- Replaces raw URLs with native Discord Link Buttons:
  - `[ Download ARM64 APK (71 MB) ]`
  - `[ Universal Build (91 MB) ]`
  - `[ GitHub Releases ]`
- Dynamic release ingestion directly from GitHub API (`v2.1.9`).

### 2. Dynamic Dropdown Select Menus (`/models`)
- Uses Discord String Select Components (`model_selector`).
- Selecting an architecture (`Qwen 2.5`, `Llama 3.2`, `Gemma 2`, `DeepSeek-R1 Distill`) edits and updates the specification embed in-place without generating extra chat messages.

### 3. Native Bug Report & Telemetry Modal (`/bug`)
- Typing `/bug` opens a native Discord Pop-Up Modal with 4 fields:
  1. `Device Model & Chipset`
  2. `Android OS Version`
  3. `Active GGUF Model & Quantization`
  4. `Error Description or Crash Stacktrace`
- Submissions automatically generate a structured, styled thread in the `#bug-reports` forum channel tagged with `inference-crash`.

### 4. 1-Click Role Onboarding (`/roles`)
- Interactive secondary buttons deployed to `#welcome`:
  - `[ Android Developer ]`
  - `[ Benchmark Tester ]`
  - `[ Core User ]`
- Instant toggle role assignment with ephemeral confirmation.

### 5. Hardware Sizing Engine (`/canirun`)
- Evaluates device memory headroom after Android OS baseline allocation.
- Computes optimal model parameter sizes, quantization targets, and estimated generation speeds.
