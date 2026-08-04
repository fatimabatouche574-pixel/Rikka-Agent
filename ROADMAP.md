# Rikka Agent — Roadmap

Fork privat `udin-petot/Rikka-Agent` dari `ExTV/rikkahub-agent` (yang merupakan fork dari `rikkahub/rikkahub`).

**Visi:** "Hermes di Android" — agent on-device yang jalan lokal, dengan UI modern (Material You 3 Expressive), provider LLM lengkap, dan semua fitur terbaik dari ekosistem RikkaHub.

## Prioritas (disepakati 2026-08)

### P1 — UI Redesign (port dari LastChat)
- [ ] Material You 3 Expressive: theme, komponen, motion, typography
- [ ] Model **catalog system** (catalog JSON + LobeHub icons + auto-detect capabilities)
- [ ] Tagging assistant + import/export konfigurasi assistant
- [ ] Rich rendering: LaTeX, code highlight, tabel (cek gap vs LastChat)
- [ ] OCR activity, TTS/STT, WebDAV backup, image-gen interface
- [ ] Riset sumber: `git fetch lastchat` → diff/dump file UI terkait (app/, catalog/)

### P2 — LLM Provider lengkap (semua provider di LastChat & AmberAgent)
- [ ] Riset daftar provider: `catalog/lastchat_catalog.json` (LastChat) + `ai/src` (AmberAgent)
- [ ] Tambah first-class: DeepSeek, Mistral, LM Studio/llama.cpp, dsb (yang belum ada)
- [ ] Implementasi: interface di modul `:ai` + UI settings (Providers)
- [ ] Verifikasi tiap provider: call test + model list

### P3 — Unlock Agent Keyboard
- [ ] Clone & build `ExTV/agent-keyboard` (fork FlorisBoard)
- [ ] Buat/atur **keystore bersama** — sign app + keyboard dengan kunci yang SAMA (syarat AIDL co-signing)
- [ ] Install keyboard, set sebagai IME, verifikasi `keyboard_read_field`, `keyboard_editor_info`, typing

### P4 — Port fitur AmberAgent & LastChat (yang belum ada)
- [ ] Search orchestration: stable service selectors + self-correction + WebView fallback
- [ ] ADR docs (`docs/adr/`) + check scripts (disiplin engineering)
- [ ] Agent kernel / runner patterns
- [ ] Evaluasi per fitur lain: novel module, LAN companion, iCloud (sesuai kebutuhan)

## Cara kerja

- **Hermes**: planning, setup, git/GitHub, review hasil, verifikasi build
- **OpenCode**: eksekusi koding (one-shot `opencode run` atau TUI background)
- **Cherry-pick** dari `lastchat`/`amber` remotes — tree beda jauh, ekspektasikan konflik; evaluasi per-commit
- Setiap fitur selesai: `./gradlew :app:assembleDebug` hijau + `./gradlew test` tetap lulus (1286+ test)

## Milestone

- **M1** — Build hijau di fork sendiri + AGENTS.md + skill Hermes (status: dalam proses)
- **M2** — UI redesign v1 (P1)
- **M3** — Provider lengkap (P2)
- **M4** — Agent keyboard aktif (P3)
- **M5** — Fitur port v1 (P4)

## Invariants yang wajib dijaga (lihat AGENTS.md)

Zero telemetry · applicationId `excp.rikkahub` · safety 3 lapis (toggle → approval → HARDLINE) · DB migration berurutan · string resources i18n · lisensi AGPL v3 + atribusi
