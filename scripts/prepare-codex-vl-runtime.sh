#!/usr/bin/env bash
set -euo pipefail

# This script downloads a fixed, checksum-pinned build artifact. It never reads or executes
# a user-pasted setup command, and it never executes content from setup-codex.sh.
readonly CODEX_VERSION="0.153.2-vl.1"
readonly ARCHIVE_NAME="codex-npm-android-arm64-${CODEX_VERSION}.tgz"
readonly ARCHIVE_URL="https://github.com/DioNanos/codex-vl/releases/download/rust-v${CODEX_VERSION}/${ARCHIVE_NAME}"
readonly ARCHIVE_SHA256="8ab7963478044c1001613745e7282d68af4a3007d3734a2e30d7020fbcf8f2a2"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly DESTINATION="${PROJECT_DIR}/app/src/main/jniLibs/arm64-v8a"
readonly TEMP_DIR="$(mktemp -d)"
trap 'rm -rf -- "${TEMP_DIR}"' EXIT

curl --fail --location --silent --show-error "${ARCHIVE_URL}" --output "${TEMP_DIR}/${ARCHIVE_NAME}"
printf '%s  %s\n' "${ARCHIVE_SHA256}" "${TEMP_DIR}/${ARCHIVE_NAME}" | sha256sum --check --status
tar -xzf "${TEMP_DIR}/${ARCHIVE_NAME}" -C "${TEMP_DIR}"

readonly VENDOR_DIR="${TEMP_DIR}/package/vendor/aarch64-linux-android/codex"
test -x "${VENDOR_DIR}/codex"
test -x "${VENDOR_DIR}/codex-code-mode-host"
test -f "${VENDOR_DIR}/libc++_shared.so"
mkdir -p "${DESTINATION}"
install -m 0755 "${VENDOR_DIR}/codex" "${DESTINATION}/libcodex_vl.so"
install -m 0755 "${VENDOR_DIR}/codex-code-mode-host" "${DESTINATION}/libcodex_code_mode_host.so"
install -m 0755 "${VENDOR_DIR}/libc++_shared.so" "${DESTINATION}/libc++_shared.so"

file "${DESTINATION}/libcodex_vl.so" | grep -q 'ARM aarch64'
file "${DESTINATION}/libcodex_code_mode_host.so" | grep -q 'ARM aarch64'
echo "Prepared checksum-verified Codex-VL ${CODEX_VERSION} Android ARM64 runtime."
