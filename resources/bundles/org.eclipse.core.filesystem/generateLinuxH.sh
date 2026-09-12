#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${SCRIPT_DIR}/src-gen"
TARGET_PACKAGE="org.eclipse.core.internal.filesystem.local.linux.ffm"
TARGET_PACKAGE_DIR="${OUTPUT_DIR}/org/eclipse/core/internal/filesystem/local/linux/ffm"
JEXTRACT="${JEXTRACT:-jextract}"

if ! command -v "${JEXTRACT}" >/dev/null 2>&1; then
	echo "jextract executable not found: ${JEXTRACT}" >&2
	echo "Set JEXTRACT=/path/to/jextract or add it to PATH." >&2
	exit 1
fi

mkdir -p "${OUTPUT_DIR}"
rm -rf "${TARGET_PACKAGE_DIR}"

MULTIARCH="$(cc -print-multiarch)"
HEADER_FILE="$(mktemp)"
trap 'rm -f "${HEADER_FILE}"' EXIT

cat >"${HEADER_FILE}" <<'EOF'
#define _GNU_SOURCE
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <errno.h>
#include <limits.h>
#include <fcntl.h>
#include <dirent.h>
EOF

"${JEXTRACT}" --output "${OUTPUT_DIR}" \
	--include-function opendir \
	--include-function readdir \
	--include-function closedir \
	--include-function dirfd \
	--include-function fstatat \
	--include-function readlinkat \
	--include-typedef DIR \
	--include-struct stat \
	--include-struct dirent \
	--include-struct timespec \
	--include-constant ENOENT \
	--include-constant AT_SYMLINK_NOFOLLOW \
	--include-constant PATH_MAX \
	--include-constant S_IFMT \
	--include-constant S_IFLNK \
	--include-constant S_IFDIR \
	--include-constant S_IRUSR \
	--include-constant S_IWUSR \
	--include-constant S_IXUSR \
	--include-constant S_IRGRP \
	--include-constant S_IWGRP \
	--include-constant S_IXGRP \
	--include-constant S_IROTH \
	--include-constant S_IWOTH \
	--include-constant S_IXOTH \
	--target-package "${TARGET_PACKAGE}" \
	--header-class-name LinuxDirent \
	--library c \
	--include-dir /usr/include \
	--include-dir "/usr/include/${MULTIARCH}" \
	"${HEADER_FILE}"
