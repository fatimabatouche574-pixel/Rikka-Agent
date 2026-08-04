<div align="center">

<img src="docs/icon.png" width="96" height="96" alt="Rikka Agent" style="border-radius: 24px" />

# Rikka Agent

**Your phone, automated.**

An Android native autonomous agent runtime (Kotlin + Jetpack Compose), forked from [RikkaHub](https://github.com/rikkahub/rikkahub) and [rikkahub-agent](https://github.com/ExTV/rikkahub-agent). Turns your phone into a local AI agent: 80+ device tools, AI-authored workflows, scheduled jobs, an in-app browser the AI drives, SSH, screen automation, slash commands, permanent memory, self-improving skills, and a remote Telegram bot. All opt-in.

<p>
  <a href="https://github.com/udin-petot/Rikka-Agent/releases"><img src="https://img.shields.io/github/v/release/udin-petot/Rikka-Agent?include_prereleases&style=flat-square&label=release&color=blue" alt="Release" /></a>
  <a href="https://github.com/udin-petot/Rikka-Agent/releases"><img src="https://img.shields.io/github/downloads/udin-petot/Rikka-Agent/total?style=flat-square&color=brightgreen" alt="Downloads" /></a>
  <a href="https://github.com/udin-petot/Rikka-Agent/stargazers"><img src="https://img.shields.io/github/stars/udin-petot/Rikka-Agent?style=flat-square&color=yellow" alt="Stars" /></a>
  <img src="https://img.shields.io/badge/platform-Android%208%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 8+" />
  <img src="https://img.shields.io/badge/language-English%20%2B%20Bahasa%20Indonesia-3DDC84?style=flat-square" alt="English + Bahasa Indonesia" />
</p>

<a href="#features">Features</a> ·
<a href="#what-makes-this-fork-different">Fork Highlights</a> ·
<a href="#quick-start">Quick Start</a> ·
<a href="#building-from-source">Build</a>

</div>

---

## What can it do?

Tell it what to do in plain language. The phone runs it in the background while you live your life.

> *"Every weekday at 9am, summarize my unread WhatsApp into one Telegram message."*
> *"If my home server's disk fills up, ping me."*
> *"Watch my notifications. If anything from my boss comes in, forward it to Telegram."*
> *"Find the PDF on my phone that mentions 'invoice' and read me the first paragraph."*
> *"Take a screenshot every 30 minutes for the next 4 hours so I can see what I actually did all afternoon."*
> *"Use Termux to build me a webpage listing everything you can do, then open it in my browser."*
> *"When I plug in headphones at home WiFi after 7pm, start my evening playlist."*
> *"Open my router's admin page, sign in with the saved password, and tell me which devices are eating the most bandwidth right now."*

Each of those is a one-line setup.

---

## What makes this fork different?

**Rikka Agent** builds on the upstream `rikkahub-agent` with a focus on making the agent truly autonomous and self-improving, plus a modern, accessible UI:

### Model Catalog (001)
- **62+ provider presets** with icons in a browsable catalog — one-tap add, no manual config
- **Auto-detected capabilities** — badge per model (chat, vision, tool calling, reasoning)
- **Network catalog updates** — new providers arrive without an app update (24h auto-refresh)
- **Per-model overrides** — edit type, modality, and abilities per model

### Agent Self-Improvement (002)
- **Rich slash commands** — `/help`, `/new`, `/clear`, `/model`, `/memory`, `/doctor`, `/undo` — shared engine between the in-app chat **and** the Telegram bot
- **Permanent memory** — the agent maintains a global memory across sessions (all memory features ON by default)
- **Session recall** — `conversation_search` + `recent_chats` tools so the agent remembers past conversations
- **Learning from mistakes** — when a tool call fails, a lesson is captured and injected into future prompts
- **Self-improving skills** — skills declare `triggers:` and `commands:` in frontmatter; the agent auto-loads relevant skills and can offer to save a procedure skill (approval-gated)

### English-First + Bahasa Indonesia (003)
- **English by default** — no more Chinese-only UI
- **Bahasa Indonesia locale** — full Indonesian translation (technical terms stay English)
- **13-language picker** — English, Bahasa Indonesia, Chinese (simplified/traditional), Japanese, Korean, Russian, Arabic, and more

### Everything upstream has
Device control (80+ tools), workflows & schedules, Telegram bot, in-app AI-driven browser, file manager, SSH, music & media, skills, sub-agents, Doctor, MCP servers, notifications & external triggers, 3-layer safety.

---

## Features

### Device Control

Tap, swipe, scroll, type, take screenshots, open apps, adjust brightness/volume, post notifications, check battery/WiFi/signal/location/sensors, read contacts & SMS, send SMS, set wallpaper, read/write NFC tags, sign and encrypt data with the Android Keystore, access external storage and SD cards, and manage ZIP archives. **80+ tools**, all built into Android. Each one stays off until you flip it on.

### Workflows & Schedules

**Workflows** — Describe a trigger and action in plain language: *"when I get home, turn the ringer off."* 19 triggers (WiFi, Bluetooth, headphones, geofence, app launch, notifications, time, charging, screen state, and more) and 14 conditions (battery thresholds, sunrise/sunset, day-of-week, foreground app, screen state) decide when each fires. Receivers register only when needed — battery drain stays minimal.

**Schedules** — Run tasks on any cadence: *"every Monday at 8am"*, *"every two hours"*, *"next Friday at 3pm."* Survives reboots and battery saver. Let the AI think at runtime, or pre-bake fixed actions that don't burn tokens.

### Telegram Bot

Talk to your assistant from anywhere. Send a question, photo, PDF, or voice note. Approval prompts use simple Yes/No buttons. When the AI needs input, it pops a tappable multiple-choice question right in the chat. Long messages arrive as downloadable files. Message bursts are paced to avoid Telegram rate limits.

### In-App Browser

A real browser built into the app. The AI clicks through cookie banners, fills search boxes, scrolls, and reads pages back to you. Streams fresh screenshots to your chat after every step. Floating chat pill lets you keep talking to the AI without leaving the page. Built-in article extraction and diff-after-action keep token costs low.

### File Manager

Find files, read them, save new ones, copy, move, rename, delete. *"Find every PDF mentioning 'invoice' on my phone"* works in one sentence. System folders outside your app's sandbox are off-limits, even if you ask.

### SSH

Save your servers once. Run commands, upload files, pull backups, check disk space, tail logs — all from chat. Pipe input into commands, write remote files, or launch long-running servers that return a PID instead of hanging. Works on WiFi or cell.

### Music & Media

Play music through Android's normal media controls: lock-screen art, headphone keys, the works. Pause, resume, adjust volume — all from chat or Telegram. Your queue survives force-stops via snapshot fallback.

### Skills

Drop a Markdown skill file and the AI gains a new playbook. A bundled catalog ships with a QR generator, Wikipedia query box, piano, interactive map, and more. Two skills enabled out of the box: an always-on agent playbook and an OpenClaw converter. Add skills from a URL or by sharing a Markdown file into the app.

### Sub-Agents

For long tasks, the main assistant dispatches focused sub-agents into clean side-contexts, optionally on smaller, cheaper models. Run multiple in parallel. Each result comes back as a single summary. `/stop` cascades cancellation through every active child in one tick.

### Doctor

A built-in health checkup. Runs a full audit of permissions, background services, database integrity, network, Termux, and diagnostics. Tap auto-fix to grant permissions, restart services, or rebuild search indexes. Also available remotely via `/doctor` on Telegram.

### MCP Servers

Connect [Model Context Protocol](https://modelcontextprotocol.io) servers and the AI gains whatever tools they expose. The AI can add, update, and manage MCP connections itself — every change is approval-gated.

### Notifications & External Triggers

The AI can read, summarize, and forward incoming notifications from apps you choose. The whitelist starts empty. Other apps (Tasker, automation tools, ADB) can hand the agent tasks through the External Automation Intent API.

### Safety & Privacy

Three layers of protection:

1. **Per-assistant toggles** — Every tool starts off. Flip on only what you want.
2. **Per-call approval** — Tools that change something ask before running.
3. **HARDLINE floor** — Genuinely dangerous commands (wipe, reboot, fork bombs, system file destruction) are blocked unconditionally.

Passwords and API keys never hit log files. Cloud backups skip saved credentials. The Telegram bot ignores everyone except your allowlist. **Zero telemetry** — no analytics, no Firebase, nothing phoning home.

---

## Quick Start

### 1. Install

Download the latest APK from [Releases](https://github.com/udin-petot/Rikka-Agent/releases/latest). Pick `app-arm64-v8a` for most phones, `app-universal` if unsure. Allow install from unknown sources, then open.

> **Note:** If you have an old debug build installed, uninstall it first — release builds are signed differently.

> **App ID:** `excp.rikkahub` — Rikka Agent installs alongside upstream RikkaHub, so you can run both side by side.

### 2. Add an LLM Provider

**Settings → Providers → pick one → paste your API key.**

- **OpenRouter** — first-class support with auto-detected model capabilities, pricing, and routing
- **Codex** — sign in with your ChatGPT account (OpenAI plan over OAuth)
- **Grok** — sign in with your xAI account (SuperGrok or X Premium+ over OAuth)
- **Local · LiteRT** — download a local model (Gemma, Qwen). No key, no network. Runs on-device with GPU acceleration where supported
- **AICore** — Pixel 8/9/10 users can enable Gemini Nano for on-device inference (currently requires the AICore Beta)
- **Model Catalog** — browse 62+ providers in Settings → Models → Catalog, one-tap add

### 3. Turn On What You Want

**Settings → Assistants → tap your assistant → Local Tools** — flip the categories you want enabled. Memory, session recall, and learning features are ON by default — opt out per assistant whenever you like.

### 4. (Optional) Telegram Bot

1. Message [@BotFather](https://t.me/BotFather) with `/newbot` to get a token
2. Message [@userinfobot](https://t.me/userinfobot) with `/start` to get your numeric user ID
3. Tell the assistant: *"Set up the Telegram bot. Token is `<token>`. My user id is `<id>`. Set me as the default chat. Enable it."*

---

## Requirements

| | |
|---|---|
| **Architecture** | arm64 or x86_64 |
| **Android** | 8.0+ (API 26), targets API 37 |
| **Storage** | ~80 MB |
| **Language** | English (default) · Bahasa Indonesia · 中文 · 日本語 · 한국어 · Русский · العربية |

---

## Building from Source

```bash
git clone https://github.com/udin-petot/Rikka-Agent.git
cd Rikka-Agent
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/`. First build is slow (Gradle distribution + deps + web-ui once).

---

## License

AGPL v3 — free for personal / non-commercial use (≤10 users). Commercial use requires a separate license. See [LICENSE](LICENSE).

**Attribution:** This project is a fork of [RikkaHub](https://github.com/rikkahub/rikkahub) (by [rikkahub](https://github.com/rikkahub)) and [rikkahub-agent](https://github.com/ExTV/rikkahub-agent) (by [ExTV](https://github.com/ExTV)). UI ideas from [LastChat](https://github.com/Cocolalilal/LastChat), architecture lessons from [AmberAgent](https://github.com/soul99soul-glitch/AmberAgent). All upstream license and attribution notices preserved.
