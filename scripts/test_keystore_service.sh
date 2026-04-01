#!/usr/bin/env bash

set -euo pipefail

PACKAGE="dev.jamescullimore.trustkeyservice"
RECEIVER="$PACKAGE/.service.KeystoreCommandReceiver"
TAG="TrustKeyServiceCommand"
ALIAS="${KEY_ALIAS:-hardware-backed-demo-key}"

usage() {
  cat <<'EOF'
Usage:
  scripts/test_keystore_service.sh generate [--strongbox] [alias]
  scripts/test_keystore_service.sh inspect [alias]
  scripts/test_keystore_service.sh encrypt <plaintext> [alias]
  scripts/test_keystore_service.sh decrypt <ciphertext_base64> <iv_base64> [alias]
  scripts/test_keystore_service.sh delete [alias]

Notes:
  - This script talks to the Android app through adb and an exported shell-only command bridge.
  - Binder stays on the Android device. adb is only the transport used by your workstation.
EOF
}

require_adb() {
  command -v adb >/dev/null 2>&1 || {
    echo "adb not found in PATH" >&2
    exit 1
  }
}

require_jq() {
  command -v jq >/dev/null 2>&1 || {
    echo "jq not found in PATH" >&2
    exit 1
  }
}

send_command() {
  local operation="$1"
  shift
  local request_id="req-$(date +%s)-$$"

  adb logcat -c >/dev/null
  adb shell am broadcast \
    -n "$RECEIVER" \
    -a "$PACKAGE.action.EXECUTE_COMMAND" \
    --es request_id "$request_id" \
    --es operation "$operation" \
    "$@" >/dev/null

  local attempt
  for attempt in $(seq 1 20); do
    local line
    line="$(adb logcat -d -s "$TAG:I" | grep "$request_id" || true)"
    if [[ -n "$line" ]]; then
      echo "$line" | sed -E 's/.*payload=//'
      return 0
    fi
    sleep 0.25
  done

  echo "Timed out waiting for service response for request $request_id" >&2
  exit 1
}

main() {
  require_adb

  local command="${1:-}"
  if [[ -z "$command" ]]; then
    usage
    exit 1
  fi
  shift

  case "$command" in
    generate)
      local strongbox="false"
      if [[ "${1:-}" == "--strongbox" ]]; then
        strongbox="true"
        shift
      fi
      local alias="${1:-$ALIAS}"
      send_command generate --es alias "$alias" --ez request_strongbox "$strongbox"
      ;;
    inspect)
      require_jq
      local alias="${1:-$ALIAS}"
      local json
      json="$(send_command inspect --es alias "$alias")"
      echo "$json"
      echo "$json" | jq -r '"VERDICT: \(.securityVerdict) | LEVEL: \(.securityLevel) | KEY_PRESENT: \(.keyPresent) | STRONGBOX_FEATURE: \(.strongBoxFeature)"'
      ;;
    encrypt)
      local plaintext="${1:-}"
      local alias="${2:-$ALIAS}"
      if [[ -z "$plaintext" ]]; then
        echo "encrypt requires plaintext" >&2
        exit 1
      fi
      send_command encrypt --es alias "$alias" --es plaintext "$plaintext"
      ;;
    decrypt)
      local ciphertext="${1:-}"
      local iv="${2:-}"
      local alias="${3:-$ALIAS}"
      if [[ -z "$ciphertext" || -z "$iv" ]]; then
        echo "decrypt requires ciphertext and iv" >&2
        exit 1
      fi
      send_command decrypt --es alias "$alias" --es ciphertext "$ciphertext" --es iv "$iv"
      ;;
    delete)
      local alias="${1:-$ALIAS}"
      send_command delete --es alias "$alias"
      ;;
    *)
      usage
      exit 1
      ;;
  esac
}

main "$@"
