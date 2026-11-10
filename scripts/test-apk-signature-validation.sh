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

# Locate apksigner to extract legitimate fingerprint for Test 1
APKSIGNER=""
if which apksigner >/dev/null 2>&1; then
  APKSIGNER="apksigner"
elif [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/build-tools" ]; then
  APKSIGNER=$(find "$ANDROID_HOME/build-tools" -name apksigner 2>/dev/null | sort -V | tail -n 1)
elif [ -d "/usr/local/lib/android/sdk/build-tools" ]; then
  APKSIGNER=$(find "/usr/local/lib/android/sdk/build-tools" -name apksigner 2>/dev/null | sort -V | tail -n 1)
fi

if [ -z "$APKSIGNER" ] || [ ! -x "$APKSIGNER" ]; then
  echo "Error: apksigner not found for running signature validation tests." >&2
  exit 1
fi

ACTUAL_FP=$("$APKSIGNER" verify --verbose --print-certs "$SIGNED_APK" | grep -i "Signer #1 certificate SHA-256 digest:" | head -n 1 | awk '{print $NF}' | tr '[:upper:]' '[:lower:]' | tr -d ':')
if [ -z "$ACTUAL_FP" ]; then
  echo "Error: Could not extract fingerprint from sample signed APK." >&2
  exit 1
fi

echo "=========================================================="
echo "Starting APK Signature Validation Test Suite"
echo "Target sample APK: $SIGNED_APK"
echo "Detected certificate fingerprint: $ACTUAL_FP"
echo "=========================================================="

# ------------------------------------------------------------------
# Test 1: Valid signature + matching certificate SHA-256
# ------------------------------------------------------------------
echo -n "[Test 1/4] Valid signature & matching SHA-256: "
set +e
"$VERIFY_SCRIPT" "$SIGNED_APK" "$ACTUAL_FP" > /tmp/test1.log 2>&1
T1_EXIT=$?
set -e
if [ $T1_EXIT -eq 0 ]; then
  echo "PASSED (exit code 0 as expected)"
else
  echo "FAILED (expected 0, got $T1_EXIT)"
  cat /tmp/test1.log
  exit 1
fi

# ------------------------------------------------------------------
# Test 2: Unsigned APK
# ------------------------------------------------------------------
echo -n "[Test 2/4] Unsigned APK (Must fail-closed): "
UNSIGNED_APK="/tmp/test_unsigned_$$.apk"
cp "$SIGNED_APK" "$UNSIGNED_APK"
# Strip all META-INF signature files to make it unsigned
zip -d "$UNSIGNED_APK" "META-INF/*" >/dev/null 2>&1 || true

set +e
"$VERIFY_SCRIPT" "$UNSIGNED_APK" "$ACTUAL_FP" > /tmp/test2.log 2>&1
T2_EXIT=$?
set -e
rm -f "$UNSIGNED_APK"

if [ $T2_EXIT -ne 0 ]; then
  echo "PASSED (failed-closed with code $T2_EXIT as expected)"
else
  echo "FAILED (expected non-zero exit code for unsigned APK!)"
  cat /tmp/test2.log
  exit 1
fi

# ------------------------------------------------------------------
# Test 3: Valid signature + wrong certificate SHA-256
# ------------------------------------------------------------------
echo -n "[Test 3/4] Valid signature & wrong SHA-256 (Must fail-closed): "
WRONG_FP="00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
set +e
"$VERIFY_SCRIPT" "$SIGNED_APK" "$WRONG_FP" > /tmp/test3.log 2>&1
T3_EXIT=$?
set -e
if [ $T3_EXIT -ne 0 ]; then
  echo "PASSED (failed-closed with code $T3_EXIT as expected)"
else
  echo "FAILED (expected non-zero exit code for mismatched fingerprint!)"
  cat /tmp/test3.log
  exit 1
fi

# ------------------------------------------------------------------
# Test 4: Missing certificate SHA-256 configuration
# ------------------------------------------------------------------
echo -n "[Test 4/4] Missing certificate SHA-256 (Must fail-closed): "
set +e
"$VERIFY_SCRIPT" "$SIGNED_APK" "" > /tmp/test4.log 2>&1
T4_EXIT=$?
set -e
if [ $T4_EXIT -ne 0 ]; then
  echo "PASSED (failed-closed with code $T4_EXIT as expected)"
else
  echo "FAILED (expected non-zero exit code for missing fingerprint!)"
  cat /tmp/test4.log
  exit 1
fi

echo "=========================================================="
echo "ALL 4 SIGNATURE VALIDATION TESTS PASSED (100% Fail-Closed)"
echo "=========================================================="
exit 0
