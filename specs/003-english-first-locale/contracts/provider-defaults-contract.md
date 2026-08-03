# Contract: Default Provider Data (English Naming)

**Owner**: `me.rerere.rikkahub.data.datastore.DefaultProviders` / `RecommendedProviders`

## Purpose

Every bundled default provider renders an English name and description (FR-005, SC-001). The eight-to-ten Chinese names called out in the spec are replaced; user-created provider entries are never rewritten.

## Name mapping

| Current `name` (DefaultProviders.kt) | Target `name` |
|---|---|
| 硅基流动 | SiliconFlow |
| 小马算力 | Xiaoma |
| 阿里云百炼 | Alibaba Qwen |
| 火山引擎 | Volcengine |
| 月之暗面 | Moonshot |
| 智谱AI开放平台 | Zhipu AI |
| 阶跃星辰 | StepFun |
| 腾讯Hunyuan | Tencent Hunyuan |
| 随想AI网关 (also RecommendedProviders.kt) | Suixiang |

## Description mapping

Chinese description/shortDescription literals → English `strings.xml` resources (localizable):

- AiHubMix (DefaultProviders.kt + RecommendedProviders.kt) — description + shortDescription + top-up/website lines
- 小马算力 — description
- 302.AI — description
- 随想AI网关 (both files) — description + shortDescription
- AckAI — description
- UnifyLLM — description

## Immutable fields (do not touch)

- `id` UUIDs (user-saved configs reference them)
- `baseUrl` (API identity — SiliconFlow stays `api.siliconflow.cn`, etc.)
- `enabled = false` (3-layer safety: providers ship OFF)
- `builtIn = true`
- `apiKey` (empty)

## Merge/upgrade behavior

Existing installs persist their own `providers` list. `PreferencesStore`'s merge only copies `builtIn`/`description`/`shortDescription` back from the bundled defaults — never `name`, `enabled`, `apiKey`, or `models`. So:

- New installs / fresh merges → English bundled names.
- Existing user-saved provider entries → left exactly as the user saved them (edge case: "existing saved configs are user data"). A user who saved the provider under its Chinese name keeps seeing it until they edit it.

## Invariants

1. No provider UUID, baseUrl, or enabled-flag change (Constitution II — providers stay disabled by default).
2. English strings localize through `values*/strings.xml` (FR-011), with zh parity entries added so the Chinese locale still reads naturally where a swept string was Chinese.
3. `RecommendedProviders.kt` uses the same English data (it duplicates AiHubMix + Suixiang for the recommend sheet).
