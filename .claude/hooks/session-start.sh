#!/bin/bash
# SessionStart hook for Claude Code cloud sessions.
#
# Sets up what the tests need, and never fails the session: each step reports what it could not
# do and moves on.
#  1. Pure-Kotlin tests (parser, typing help, PIN hashing, backup format) via tools/jvm-check.
#     Needs only Maven Central, so they work even where Google's servers are blocked.
#  2. The Android SDK, when dl.google.com is reachable, so ./gradlew can build and test every
#     module. The full build also needs github.com (sherpa-onnx AAR, speech model) and
#     api.foojay.io (the JDK 21 named in gradle/gradle-daemon-jvm.properties).
set -uo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}" || exit 0
chmod +x gradlew 2>/dev/null || true

log() { echo "[session-start] $*" >&2; }
reachable() { curl -sS -o /dev/null --max-time 10 -I "$1" >/dev/null 2>&1; }

# ------------------------------------------------------------------ 1. JVM-only checks
log "Preparing pure-Kotlin tests (tools/jvm-check)…"
if ./gradlew -q -p tools/jvm-check testClasses >/tmp/jvm-check-warmup.log 2>&1; then
  log "Pure-Kotlin tests ready: ./gradlew -p tools/jvm-check test"
else
  log "Could not prepare tools/jvm-check (see /tmp/jvm-check-warmup.log); continuing."
fi

# ------------------------------------------------------------------ 2. Android SDK
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-13114758_latest.zip"

if [ -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ] && [ -d "$SDK/platforms/android-36" ]; then
  log "Android SDK already present at $SDK"
elif reachable "https://dl.google.com/android/repository/repository2-3.xml"; then
  log "Installing Android SDK into $SDK…"
  mkdir -p "$SDK/cmdline-tools"
  if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
    tmp="$(mktemp -d)"
    if curl -sSfL --max-time 300 -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP" \
      && unzip -q "$tmp/tools.zip" -d "$tmp"; then
      rm -rf "$SDK/cmdline-tools/latest"
      mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
    else
      log "Could not download the Android command-line tools; Android modules will not build."
    fi
    rm -rf "$tmp"
  fi
  if [ -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
    yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null 2>&1 || true
    "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" \
      "platforms;android-36" "build-tools;36.0.0" "platform-tools" >/tmp/sdkmanager.log 2>&1 \
      || log "sdkmanager failed (see /tmp/sdkmanager.log)"
  fi
else
  log "dl.google.com is blocked by this environment's network policy: Android modules cannot build."
  log "Allow dl.google.com, maven.google.com, github.com, objects.githubusercontent.com and api.foojay.io"
  log "in the environment's network settings to enable full builds."
fi

if [ -d "$SDK/platforms/android-36" ]; then
  grep -q '^sdk.dir=' local.properties 2>/dev/null || echo "sdk.dir=$SDK" >> local.properties
  if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
    {
      echo "export ANDROID_HOME=\"$SDK\""
      echo "export ANDROID_SDK_ROOT=\"$SDK\""
    } >> "$CLAUDE_ENV_FILE"
  fi
  log "Warming up the Android build (dependencies, sherpa-onnx AAR, speech model)…"
  if ./gradlew -q :core:nlu:testClasses :core:data:compileDebugUnitTestKotlin >/tmp/android-warmup.log 2>&1; then
    log "Android build ready: ./gradlew :core:nlu:test :core:data:testDebugUnitTest :app:testDebugUnitTest"
  else
    log "Android warm-up failed (see /tmp/android-warmup.log); github.com or api.foojay.io may be blocked."
  fi
fi

exit 0
