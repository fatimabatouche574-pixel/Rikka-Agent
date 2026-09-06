# Codex-VL chat routing

## Why the connection test was not enough

The Codex-VL settings page owns a separate encrypted Provider configuration. The
normal Rikka Agent Provider list is intentionally not modified by that page. A
conversation still uses the `Assistant.agentRuntime` value stored with its assistant.
Older builds left the default assistant on `RIKKA_NATIVE`, so a message could be
validated against (and rejected by) the disabled `RikkaHub` chat Provider even when
the Codex-VL connection test returned HTTP 200.

## Current behavior

The `Save & Enable` action now has a visible **Use Codex-VL for current assistant**
option. It is enabled automatically when Codex-VL is enabled, and can be turned off
to keep configuring the Provider without changing the assistant. When selected, the
current assistant is persisted with `agentRuntime = CODEX_VL`; the next message uses
`ChatService.handleCodexVLMessage()` and the persistent Codex-VL app-server thread.

The native model picker, web-search toggle, and native reasoning control are hidden
for a Codex-VL conversation because those controls configure the Rikka chat loop, not
the Codex runtime. The chat header and input strip show the configured Codex model and
`Responses API` instead of displaying the unrelated native Provider.

Conversations resolve their own persisted assistant id for routing and model display.
Changing the assistant in the drawer therefore cannot silently switch an existing
conversation between Native and Codex runtimes.

## User flow

1. Open **Settings → Codex-VL**.
2. Paste and parse the setup command, or enter Base URL, API key, and model manually.
3. Keep **Enable Codex-VL** and **Use Codex-VL for current assistant** enabled.
4. Tap **Test connection**, then **Save & Enable**.
5. Return to the current/new conversation. Its subtitle should say
   `Codex-VL · <model> (Responses API)`.

To switch an individual assistant later, use **Assistant → Basic → Agent Runtime**.
Choosing **Rikka Agent Native** restores the original Provider path without changing
the encrypted Codex configuration.

## Safety

This routing change does not enable the native `RikkaHub` Provider, does not invoke
OpenAI OAuth, and does not execute an imported setup command. The API key remains in
the existing Keystore-backed Codex store.
