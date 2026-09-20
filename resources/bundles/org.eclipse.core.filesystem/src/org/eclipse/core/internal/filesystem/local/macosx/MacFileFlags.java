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

import com.apple.macos.UnixStat;
import com.apple.macos.stat;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
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

public final class MacFileFlags {
	public static final boolean CHFLAGS_SUPPORTED = Platform.OS.isMac() && isSupported0();
	static final int UF_IMMUTABLE = UnixStat.UF_IMMUTABLE();
	private static final int SF_IMMUTABLE = UnixStat.SF_IMMUTABLE();
	private static final int IMMUTABLE_FLAGS = UF_IMMUTABLE | SF_IMMUTABLE;
	private static final int ENOENT = 2;
	private static final int ENOTDIR = 20;
	private static final long UINT_MASK = 0xffff_ffffL;
	private static final Linker NATIVE_LINKER = Linker.nativeLinker();

	private static final StructLayout ERRNO_CAPTURE_LAYOUT = Linker.Option.captureStateLayout();
	private static final VarHandle ERRNO = ERRNO_CAPTURE_LAYOUT.varHandle(java.lang.foreign.MemoryLayout.PathElement.groupElement("errno")); //$NON-NLS-1$

	private MacFileFlags() {
	}

	private static boolean isSupported0() {
		try {
			UnixStat.lstat$address();
			UnixStat.chflags$address();
			return true;
		} catch (Throwable e) {
			return false;
		}
	}

	public static boolean isImmutable(int flags) {
		return (flags & IMMUTABLE_FLAGS) != 0;
	}

	public static boolean hasUserImmutable(int flags) {
		return (flags & UF_IMMUTABLE) != 0;
	}

	public static int withUserImmutable(int flags, boolean immutable) {
		return immutable ? flags | UF_IMMUTABLE : flags & ~UF_IMMUTABLE;
	}

	public static int read(Path path) throws IOException {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment capturedErrno = arena.allocate(ERRNO_CAPTURE_LAYOUT);
			MemorySegment nativePath = allocatePath(path, arena);
			MemorySegment statBuffer = arena.allocate(stat.layout());
			if (lstat(capturedErrno, nativePath, statBuffer) != 0) {
				throw error("lstat", path, getErrno(capturedErrno)); //$NON-NLS-1$
			}
			return stat.st_flags(statBuffer);
		}
	}

	public static void write(Path path, int flags) throws IOException {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment capturedErrno = arena.allocate(ERRNO_CAPTURE_LAYOUT);
			MemorySegment nativePath = allocatePath(path, arena);
			if (chflags(capturedErrno, nativePath, Integer.toUnsignedLong(flags)) != 0) {
				throw error("chflags", path, getErrno(capturedErrno)); //$NON-NLS-1$
			}
		}
	}

	private static int lstat(MemorySegment capturedErrno, MemorySegment nativePath, MemorySegment statBuffer) {
		try {
			return (int) NativeAccess.LSTAT_HANDLE.invokeExact(capturedErrno, nativePath, statBuffer);
		} catch (Error | RuntimeException e) {
			throw e;
		} catch (Throwable e) {
			throw new AssertionError("should not reach here", e); //$NON-NLS-1$
		}
	}

	private static int chflags(MemorySegment capturedErrno, MemorySegment nativePath, long flags) {
		try {
			return (int) NativeAccess.CHFLAGS_HANDLE.invokeExact(capturedErrno, nativePath, (int) (flags & UINT_MASK));
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

	private static final class NativeAccess {
		private static final MethodHandle LSTAT_HANDLE = NATIVE_LINKER.downcallHandle(UnixStat.lstat$address(),
				UnixStat.lstat$descriptor(), Linker.Option.captureCallState("errno")); //$NON-NLS-1$
		private static final MethodHandle CHFLAGS_HANDLE = NATIVE_LINKER.downcallHandle(UnixStat.chflags$address(),
				UnixStat.chflags$descriptor(), Linker.Option.captureCallState("errno")); //$NON-NLS-1$
	}
}
