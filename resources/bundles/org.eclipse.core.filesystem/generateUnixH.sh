#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../../.." && pwd)"

case "$(uname -s)-$(uname -m)" in
	Darwin-arm64)
		JEXTRACT_ARCHIVE="${REPO_ROOT}/openjdk-25-jextract+2-4_macos-aarch64_bin.tar.gz"
		;;
	Darwin-x86_64)
		JEXTRACT_ARCHIVE="${REPO_ROOT}/openjdk-25-jextract+2-4_macos-x64_bin.tar.gz"
		;;
	Linux-x86_64)
		JEXTRACT_ARCHIVE="${REPO_ROOT}/openjdk-25-jextract+2-4_linux-x64_bin.tar.gz"
		;;
	*)
		echo "Unsupported platform: $(uname -s)-$(uname -m)" >&2
		exit 1
		;;
esac

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

tar -xf "${JEXTRACT_ARCHIVE}" -C "${TMP_DIR}"

"${TMP_DIR}/jextract-25/bin/jextract" --output "${SCRIPT_DIR}/src-gen" \
	--include-function lstat \
	--include-function chflags \
	--include-constant UF_IMMUTABLE \
	--include-constant SF_IMMUTABLE \
	--include-struct timespec \
	--include-struct stat \
	--target-package com.apple.macos \
	--header-class-name UnixStat \
	"${SCRIPT_DIR}/natives/unix/macos_stat.h"
