#!/usr/bin/env bash
set -euo pipefail

#
# Test suite for APK signature validation logic.
# Tests 4 distinct scenarios:
#   1. Valid signature + matching certificate SHA-256 (Must PASS)
#   2. Unsigned APK (Must FAIL - fail-closed)
#   3. Valid signature + wrong certificate SHA-256 (Must FAIL - fail-closed)
#   4. Missing certificate SHA-256 input (Must FAIL - fail-closed)
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERIFY_SCRIPT="$SCRIPT_DIR/verify-apk-signature.sh"
SIGNED_APK="${1:-}"

if [ -z "$SIGNED_APK" ] || [ ! -f "$SIGNED_APK" ]; then
  echo "Usage: $0 <path-to-signed-apk>" >&2
  exit 1
fi

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

echo "=== APK Signature Validation Test Suite Initialization ==="
echo "Resolving apksigner..."
APKSIGNER=$(find_apksigner || true)

if [ -z "$APKSIGNER" ]; then
  echo "Error: apksigner binary could not be found." >&2
  exit 1
fi

chmod +x "$APKSIGNER" 2>/dev/null || true
echo "Using apksigner: $APKSIGNER"
echo "Inspecting sample APK: $SIGNED_APK"

set +e
RAW_VERIFY=$("$APKSIGNER" verify --verbose --print-certs "$SIGNED_APK" 2>&1)
VERIFY_STATUS=$?
set -e

echo "=== RAW APKSIGNER VERIFY OUTPUT ==="
echo "$RAW_VERIFY"
echo "=== END RAW APKSIGNER VERIFY OUTPUT ==="

if [ $VERIFY_STATUS -ne 0 ]; then
  echo "Error: Initial verification of sample APK failed (status $VERIFY_STATUS):" >&2
  echo "$RAW_VERIFY" >&2
  exit 1
fi

ACTUAL_FP=$(echo "$RAW_VERIFY" | grep -i "certificate SHA-256" | head -n 1 | awk -F: '{print $NF}' | tr -d ' \t\r\n:' | tr '[:upper:]' '[:lower:]' || true)

if [ -z "$ACTUAL_FP" ]; then
  echo "Error: Could not extract certificate SHA-256 digest from apksigner output." >&2
  exit 1
fi

echo "Detected certificate SHA-256 fingerprint: $ACTUAL_FP"
echo "=========================================================="
echo "Executing test scenarios..."

# ------------------------------------------------------------------
# Test 1: Valid signature + matching certificate SHA-256
# ------------------------------------------------------------------
echo -n "[Test 1/4] Valid signature & matching SHA-256: "
set +e
T1_OUTPUT=$("$VERIFY_SCRIPT" "$SIGNED_APK" "$ACTUAL_FP" 2>&1)
T1_EXIT=$?
set -e

if [ $T1_EXIT -eq 0 ]; then
  echo "PASSED (exit code 0 as expected)"
else
  echo "FAILED (expected 0, got $T1_EXIT)"
  echo "$T1_OUTPUT"
  exit 1
fi

# ------------------------------------------------------------------
# Test 2: Unsigned APK
# ------------------------------------------------------------------
echo -n "[Test 2/4] Unsigned APK (Must fail-closed): "
UNSIGNED_APK="/tmp/test_unsigned_$$.apk"

# Strip all META-INF signature files using Python zipfile (zero external tool dependency)
python3 - "$SIGNED_APK" "$UNSIGNED_APK" << 'EOF'
import sys, zipfile
src = sys.argv[1]
dst = sys.argv[2]
with zipfile.ZipFile(src, 'r') as zin, zipfile.ZipFile(dst, 'w') as zout:
    for item in zin.infolist():
        if not item.filename.startswith('META-INF/'):
            zout.writestr(item, zin.read(item.filename))
EOF

set +e
T2_OUTPUT=$("$VERIFY_SCRIPT" "$UNSIGNED_APK" "$ACTUAL_FP" 2>&1)
T2_EXIT=$?
set -e
rm -f "$UNSIGNED_APK"

if [ $T2_EXIT -ne 0 ]; then
  echo "PASSED (failed-closed with code $T2_EXIT as expected)"
else
  echo "FAILED (expected non-zero exit code for unsigned APK!)"
  echo "$T2_OUTPUT"
  exit 1
fi

# ------------------------------------------------------------------
# Test 3: Valid signature + wrong certificate SHA-256
# ------------------------------------------------------------------
echo -n "[Test 3/4] Valid signature & wrong SHA-256 (Must fail-closed): "
WRONG_FP="00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
set +e
T3_OUTPUT=$("$VERIFY_SCRIPT" "$SIGNED_APK" "$WRONG_FP" 2>&1)
T3_EXIT=$?
set -e

if [ $T3_EXIT -ne 0 ]; then
  echo "PASSED (failed-closed with code $T3_EXIT as expected)"
else
  echo "FAILED (expected non-zero exit code for mismatched fingerprint!)"
  echo "$T3_OUTPUT"
  exit 1
fi

# ------------------------------------------------------------------
# Test 4: Missing certificate SHA-256 configuration
# ------------------------------------------------------------------
echo -n "[Test 4/4] Missing certificate SHA-256 (Must fail-closed): "
set +e
T4_OUTPUT=$("$VERIFY_SCRIPT" "$SIGNED_APK" "" 2>&1)
T4_EXIT=$?
set -e

if [ $T4_EXIT -ne 0 ]; then
  echo "PASSED (failed-closed with code $T4_EXIT as expected)"
else
  echo "FAILED (expected non-zero exit code for missing fingerprint!)"
  echo "$T4_OUTPUT"
  exit 1
fi

echo "=========================================================="
echo "ALL 4 SIGNATURE VALIDATION TESTS PASSED (100% Fail-Closed)"
echo "=========================================================="
exit 0
