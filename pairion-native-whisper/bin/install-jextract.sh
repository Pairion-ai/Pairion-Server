#!/usr/bin/env bash
# install-jextract.sh — Downloads and installs jextract 21 for macOS x64 (runs under Rosetta on ARM64)
#
# Usage: ./install-jextract.sh
# Installs to: ~/.pairion/tools/jextract-21/

set -euo pipefail

VERSION="21-jextract+1-2"
EXPECTED_SHA256="6183f3d079ed531cc5a332e6d86c0abfbc5d001f1e85f721ebc5232204c987a2"
URL="https://download.java.net/java/early_access/jextract/21/1/openjdk-21-jextract+1-2_macos-x64_bin.tar.gz"
INSTALL_DIR="${HOME}/.pairion/tools/jextract-21"

echo "Installing jextract ${VERSION}..."
echo "Download URL: ${URL}"
echo "Install directory: ${INSTALL_DIR}"

TMPFILE=$(mktemp /tmp/jextract-XXXXXX.tar.gz)
trap "rm -f ${TMPFILE}" EXIT

curl -L "${URL}" -o "${TMPFILE}" --progress-bar

echo "Verifying SHA-256..."
ACTUAL_SHA=$(shasum -a 256 "${TMPFILE}" | cut -d' ' -f1)
if [ "${ACTUAL_SHA}" != "${EXPECTED_SHA256}" ]; then
    echo "ERROR: SHA-256 mismatch!"
    echo "  Expected: ${EXPECTED_SHA256}"
    echo "  Actual:   ${ACTUAL_SHA}"
    exit 1
fi
echo "SHA-256 verified."

rm -rf "${INSTALL_DIR}"
mkdir -p "${INSTALL_DIR}"
tar xzf "${TMPFILE}" -C "${INSTALL_DIR}" --strip-components=1

echo ""
echo "Verifying installation..."
arch -x86_64 "${INSTALL_DIR}/bin/jextract" --version 2>&1 || true
echo ""
echo "jextract ${VERSION} installed at ${INSTALL_DIR}"
echo "Note: On Apple Silicon, run via Rosetta: arch -x86_64 ${INSTALL_DIR}/bin/jextract ..."
