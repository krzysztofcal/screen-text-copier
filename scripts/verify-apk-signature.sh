#!/usr/bin/env bash
set -euo pipefail

#
# Cryptographic APK signature verification with SHA-256 certificate fingerprint pinning.
# Fails closed on any discrepancy, missing tool, invalid signature, or wrong fingerprint.
#
# Usage:
#   ./scripts/verify-apk-signature.sh <path-to-apk> <expected-signer-sha256>
#

APK_PATH="${1:-}"
EXPECTED_SHA256="${2:-}"

if [ -z "$APK_PATH" ]; then
  echo "::error::[verify-apk-signature] No APK path provided. Verification failed-closed." >&2
  exit 1
fi

if [ ! -f "$APK_PATH" ]; then
  echo "::error::[verify-apk-signature] Target APK does not exist at: $APK_PATH. Verification failed-closed." >&2
  exit 1
fi

if [ -z "$EXPECTED_SHA256" ]; then
  echo "::error::[verify-apk-signature] Expected certificate SHA-256 is empty or not configured. Verification failed-closed." >&2
  exit 1
fi

# Locate apksigner binary
APKSIGNER=""
if which apksigner >/dev/null 2>&1; then
  APKSIGNER="apksigner"
elif [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/build-tools" ]; then
  APKSIGNER=$(find "$ANDROID_HOME/build-tools" -name apksigner 2>/dev/null | sort -V | tail -n 1)
elif [ -d "/usr/local/lib/android/sdk/build-tools" ]; then
  APKSIGNER=$(find "/usr/local/lib/android/sdk/build-tools" -name apksigner 2>/dev/null | sort -V | tail -n 1)
fi

if [ -z "$APKSIGNER" ] || [ ! -x "$APKSIGNER" ]; then
  echo "::error::[verify-apk-signature] apksigner binary could not be located in environment. Verification failed-closed." >&2
  exit 1
fi

echo "[verify-apk-signature] Tool: $APKSIGNER"
echo "[verify-apk-signature] Target APK: $APK_PATH"

# Run cryptographic verification with verbose output and certificate printing
set +e
VERIFY_OUTPUT=$("$APKSIGNER" verify --verbose --print-certs "$APK_PATH" 2>&1)
VERIFY_EXIT=$?
set -e

if [ $VERIFY_EXIT -ne 0 ]; then
  echo "::error::[verify-apk-signature] Cryptographic signature check failed (exit code $VERIFY_EXIT):" >&2
  echo "$VERIFY_OUTPUT" >&2
  exit 1
fi

if ! echo "$VERIFY_OUTPUT" | grep -q "Verifies"; then
  echo "::error::[verify-apk-signature] APK failed cryptographic verification (status does not verify):" >&2
  echo "$VERIFY_OUTPUT" >&2
  exit 1
fi

# Extract Signer #1 certificate SHA-256 digest
ACTUAL_SHA256=$(echo "$VERIFY_OUTPUT" | grep -i "Signer #1 certificate SHA-256 digest:" | head -n 1 | awk '{print $NF}' | tr '[:upper:]' '[:lower:]' | tr -d ':')
NORMALIZED_EXPECTED=$(echo "$EXPECTED_SHA256" | tr '[:upper:]' '[:lower:]' | tr -d ':')

if [ -z "$ACTUAL_SHA256" ]; then
  echo "::error::[verify-apk-signature] Could not extract certificate SHA-256 digest from apksigner output." >&2
  echo "$VERIFY_OUTPUT" >&2
  exit 1
fi

if [ "$ACTUAL_SHA256" != "$NORMALIZED_EXPECTED" ]; then
  echo "::error::[verify-apk-signature] Certificate fingerprint mismatch (fail-closed)!" >&2
  echo "Expected SHA-256: $NORMALIZED_EXPECTED" >&2
  echo "Actual SHA-256:   $ACTUAL_SHA256" >&2
  exit 1
fi

echo "✓ APK signature cryptographically verified. Signer #1 certificate SHA-256: $ACTUAL_SHA256"
exit 0
