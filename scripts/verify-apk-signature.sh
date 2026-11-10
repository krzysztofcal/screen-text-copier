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
find_apksigner() {
  if command -v apksigner >/dev/null 2>&1; then
    command -v apksigner
    return 0
  fi
  for candidate in \
    "${ANDROID_HOME:-}/build-tools/34.0.0/apksigner" \
    "/usr/local/lib/android/sdk/build-tools/34.0.0/apksigner" \
    "${ANDROID_SDK_ROOT:-}/build-tools/34.0.0/apksigner"
  do
    if [ -f "$candidate" ]; then
      echo "$candidate"
      return 0
    fi
  done
  for dir in \
    "${ANDROID_HOME:-}/build-tools" \
    "${ANDROID_SDK_ROOT:-}/build-tools" \
    "/usr/local/lib/android/sdk/build-tools" \
    "/opt/android-sdk/build-tools" \
    "/opt/android/build-tools" \
    "/home/runner/android-sdk/build-tools"
  do
    if [ -d "$dir" ]; then
      local cand
      cand=$(find "$dir" -maxdepth 2 -name apksigner -type f 2>/dev/null | sort -V | tail -n 1)
      if [ -n "$cand" ]; then
        echo "$cand"
        return 0
      fi
    fi
  done
  return 1
}

APKSIGNER=$(find_apksigner || true)

if [ -z "$APKSIGNER" ]; then
  echo "::error::[verify-apk-signature] apksigner binary could not be located in environment. Verification failed-closed." >&2
  exit 1
fi

chmod +x "$APKSIGNER" 2>/dev/null || true

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

# Extract certificate SHA-256 digest
ACTUAL_SHA256=$(echo "$VERIFY_OUTPUT" | grep -i "certificate SHA-256" | head -n 1 | awk -F: '{print $NF}' | tr -d ' \t\r\n:' | tr '[:upper:]' '[:lower:]' || true)
NORMALIZED_EXPECTED=$(echo "$EXPECTED_SHA256" | tr -d ' \t\r\n:' | tr '[:upper:]' '[:lower:]')

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

echo "[verify-apk-signature] SUCCESS: APK is cryptographically verified with matching pinned SHA-256 fingerprint: $ACTUAL_SHA256"
exit 0
