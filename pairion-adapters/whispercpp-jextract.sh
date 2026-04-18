#!/usr/bin/env bash
# whispercpp-jextract.sh — Regenerates FFM bindings from whisper.h
#
# Prerequisites:
#   - jextract installed (https://jdk.java.net/jextract/)
#   - whisper.cpp source cloned or whisper.h available locally
#
# Usage:
#   ./whispercpp-jextract.sh /path/to/whisper.h
#
# Output:
#   src/main/java/com/pairion/adapters/stt/whispercpp/ffm/
#
# The generated files should be committed to the repository.
# Regenerate when updating the whisper.cpp version.

set -euo pipefail

WHISPER_H="${1:?Usage: $0 /path/to/whisper.h}"
OUTPUT_DIR="src/main/java/com/pairion/adapters/stt/whispercpp/ffm"

if ! command -v jextract &>/dev/null; then
    echo "ERROR: jextract not found on PATH."
    echo "Install from: https://jdk.java.net/jextract/"
    exit 1
fi

echo "Generating FFM bindings from: $WHISPER_H"
echo "Output directory: $OUTPUT_DIR"

jextract \
    --source \
    --target-package com.pairion.adapters.stt.whispercpp.ffm \
    --output "$OUTPUT_DIR" \
    --header-class-name WhisperCpp \
    --include-function whisper_init_from_file_with_params \
    --include-function whisper_full \
    --include-function whisper_full_default_params \
    --include-function whisper_full_n_segments \
    --include-function whisper_full_get_segment_text \
    --include-function whisper_free \
    --include-function whisper_init_default_params \
    --include-struct whisper_full_params \
    --include-struct whisper_context_params \
    "$WHISPER_H"

echo "FFM bindings generated successfully."
echo "Add autogen notice to generated files and commit."
