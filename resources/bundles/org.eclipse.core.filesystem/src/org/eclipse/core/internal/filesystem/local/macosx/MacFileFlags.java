/*******************************************************************************
 * Copyright (c) 2026 Hannes Wellmann and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.core.internal.filesystem.local.macosx;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.eclipse.core.internal.filesystem.local.Convert;
import org.eclipse.core.runtime.Platform;

final class MacFileFlags {
	static final int UF_IMMUTABLE = 0x00000002;
	private static final int SF_IMMUTABLE = 0x00020000;
	private static final int IMMUTABLE_FLAGS = UF_IMMUTABLE | SF_IMMUTABLE;
	private static final int ENOENT = 2;
	private static final int ENOTDIR = 20;

	private static final boolean SUPPORTED_ARCH = Platform.ARCH_X86_64.equals(Platform.getOSArch())
			|| Platform.ARCH_AARCH64.equals(Platform.getOSArch());

	/*
	 * macOS uses the LP64 Darwin struct stat layout for both x86_64 and aarch64:
	 * dev_t(4), mode_t(2), nlink_t(2), ino64_t(8), uid_t(4), gid_t(4), dev_t(4),
	 * pad(4), 4x timespec(16), off_t(8), blkcnt_t(8), blksize_t(4), st_flags(4),
	 * st_gen(4), st_lspare(4), st_qspare[2](16).
	 */
	private static final long STAT_SIZE = 144;
	private static final long ST_FLAGS_OFFSET = 116;

	private static final StructLayout ERRNO_CAPTURE_LAYOUT = Linker.Option.captureStateLayout();
	private static final VarHandle ERRNO = ERRNO_CAPTURE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("errno")); //$NON-NLS-1$

	private static final MethodHandle STAT_HANDLE = downcall("stat", //$NON-NLS-1$
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final MethodHandle CHFLAGS_HANDLE = downcall("chflags", //$NON-NLS-1$
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

	private MacFileFlags() {
	}

	static boolean isSupported() {
		return SUPPORTED_ARCH && STAT_HANDLE != null && CHFLAGS_HANDLE != null;
	}

	static boolean isImmutable(int flags) {
		return (flags & IMMUTABLE_FLAGS) != 0;
	}

	static boolean hasUserImmutable(int flags) {
		return (flags & UF_IMMUTABLE) != 0;
	}

	static int withUserImmutable(int flags, boolean immutable) {
		return immutable ? flags | UF_IMMUTABLE : flags & ~UF_IMMUTABLE;
	}

	static int read(Path path) throws IOException {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment capturedErrno = arena.allocate(ERRNO_CAPTURE_LAYOUT);
			MemorySegment nativePath = allocatePath(path, arena);
			MemorySegment statBuffer = arena.allocate(STAT_SIZE, Long.BYTES);
			if (stat(capturedErrno, nativePath, statBuffer) != 0) {
				throw error("stat", path, getErrno(capturedErrno)); //$NON-NLS-1$
			}
			return statBuffer.get(ValueLayout.JAVA_INT, ST_FLAGS_OFFSET);
		}
	}

	static void write(Path path, int flags) throws IOException {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment capturedErrno = arena.allocate(ERRNO_CAPTURE_LAYOUT);
			MemorySegment nativePath = allocatePath(path, arena);
			if (chflags(capturedErrno, nativePath, flags) != 0) {
				throw error("chflags", path, getErrno(capturedErrno)); //$NON-NLS-1$
			}
		}
	}

	private static MethodHandle downcall(String symbol, FunctionDescriptor descriptor) {
		return Linker.nativeLinker().defaultLookup().find(symbol)
				.map(address -> Linker.nativeLinker().downcallHandle(address, descriptor, Linker.Option.captureCallState("errno"))) //$NON-NLS-1$
				.orElse(null);
	}

	private static int stat(MemorySegment capturedErrno, MemorySegment nativePath, MemorySegment statBuffer) {
		try {
			return (int) STAT_HANDLE.invokeExact(capturedErrno, nativePath, statBuffer);
		} catch (Error | RuntimeException e) {
			throw e;
		} catch (Throwable e) {
			throw new AssertionError("should not reach here", e); //$NON-NLS-1$
		}
	}

	private static int chflags(MemorySegment capturedErrno, MemorySegment nativePath, int flags) {
		try {
			return (int) CHFLAGS_HANDLE.invokeExact(capturedErrno, nativePath, flags);
		} catch (Error | RuntimeException e) {
			throw e;
		} catch (Throwable e) {
			throw new AssertionError("should not reach here", e); //$NON-NLS-1$
		}
	}

	private static int getErrno(MemorySegment capturedErrno) {
		return (int) ERRNO.get(capturedErrno, 0L);
	}

	private static MemorySegment allocatePath(Path path, Arena arena) {
		byte[] pathBytes = Convert.toPlatformBytes(path.toString());
		MemorySegment nativePath = arena.allocate(pathBytes.length + 1L);
		nativePath.asSlice(0, pathBytes.length).copyFrom(MemorySegment.ofArray(pathBytes));
		nativePath.set(ValueLayout.JAVA_BYTE, pathBytes.length, (byte) 0);
		return nativePath;
	}

	private static IOException error(String operation, Path path, int errno) {
		if (errno == ENOENT || errno == ENOTDIR) {
			return new NoSuchFileException(path.toString());
		}
		return new FileSystemException(path.toString(), null, operation + " failed with errno " + errno); //$NON-NLS-1$
	}
}
