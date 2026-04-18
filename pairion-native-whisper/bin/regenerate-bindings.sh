#!/usr/bin/env bash
# regenerate-bindings.sh — Regenerates jextract FFM bindings from the built whisper.h
#
# Prerequisites:
#   - jextract installed (run install-jextract.sh first)
#   - whisper.cpp built (run mvn generate-resources -pl pairion-native-whisper first)
#
# Usage: ./regenerate-bindings.sh
# Output: pairion-native-whisper/target/generated-sources/jextract/

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MODULE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
JEXTRACT="${HOME}/.pairion/tools/jextract-21/bin/jextract"
WHISPER_SRC="${MODULE_DIR}/target/whisper.cpp-src"
OUT="${MODULE_DIR}/target/generated-sources/jextract"

if [ ! -x "${JEXTRACT}" ]; then
    echo "ERROR: jextract not found at ${JEXTRACT}"
    echo "Run install-jextract.sh first."
    exit 1
fi

if [ ! -f "${WHISPER_SRC}/include/whisper.h" ]; then
    echo "ERROR: whisper.h not found at ${WHISPER_SRC}/include/whisper.h"
    echo "Run: mvn generate-resources -pl pairion-native-whisper"
    exit 1
fi

echo "Generating jextract bindings..."
echo "  Source: ${WHISPER_SRC}/include/whisper.h"
echo "  Output: ${OUT}"

rm -rf "${OUT}"

arch -x86_64 "${JEXTRACT}" \
    --source \
    --output "${OUT}" \
    --target-package com.pairion.nativelib.whisper \
    --header-class-name WhisperBindings \
    -I "${WHISPER_SRC}/ggml/include" \
    -I "${WHISPER_SRC}/include" \
    --include-function whisper_init_from_file_with_params \
    --include-function whisper_context_default_params_by_ref \
    --include-function whisper_full_default_params_by_ref \
    --include-function whisper_full \
    --include-function whisper_full_n_segments \
    --include-function whisper_full_get_segment_text \
    --include-function whisper_free \
    --include-struct whisper_context_params \
    --include-struct whisper_full_params \
    "${WHISPER_SRC}/include/whisper.h"

echo ""
echo "Bindings generated. Files:"
find "${OUT}" -name '*.java' | wc -l
echo "java files in ${OUT}"
