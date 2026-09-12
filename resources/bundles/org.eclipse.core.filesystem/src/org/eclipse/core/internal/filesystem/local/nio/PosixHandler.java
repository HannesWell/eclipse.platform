/*******************************************************************************
 * Copyright (c) 2013, 2016 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Chris McGee (IBM) - Bug 380325 - Release filesystem fragment providing Java 7 NIO2 support
 *     Sergey Prigogin (Google) - ongoing development
 *******************************************************************************/
package org.eclipse.core.internal.filesystem.local.nio;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.provider.FileInfo;
import org.eclipse.core.internal.filesystem.local.Convert;
import org.eclipse.core.internal.filesystem.local.NativeHandler;
import org.eclipse.core.internal.filesystem.local.linux.ffm.LinuxDirent;
import org.eclipse.core.internal.filesystem.local.linux.ffm.dirent;
import org.eclipse.core.internal.filesystem.local.linux.ffm.stat;
import org.eclipse.core.internal.filesystem.local.linux.ffm.timespec;
import org.eclipse.core.runtime.Platform;

/**
 * NativeHandler for POSIX file systems. It uses Java NIO for file attributes and
 * a Linux-specific FFM-based directory listing fast path when available.
 */
public class PosixHandler extends NativeHandler {
	private static final int ATTRIBUTES = EFS.ATTRIBUTE_SYMLINK | EFS.ATTRIBUTE_LINK_TARGET // symbolic link support
			| EFS.ATTRIBUTE_READ_ONLY | EFS.ATTRIBUTE_EXECUTABLE // mapped to owner read and owner execute via FileInfo implementation
			| EFS.ATTRIBUTE_OWNER_READ | EFS.ATTRIBUTE_OWNER_WRITE | EFS.ATTRIBUTE_OWNER_EXECUTE // owner
			| EFS.ATTRIBUTE_GROUP_READ | EFS.ATTRIBUTE_GROUP_WRITE | EFS.ATTRIBUTE_GROUP_EXECUTE // group
			| EFS.ATTRIBUTE_OTHER_READ | EFS.ATTRIBUTE_OTHER_WRITE | EFS.ATTRIBUTE_OTHER_EXECUTE; // other
	private static final boolean USE_MILLISECOND_RESOLUTION = Boolean.parseBoolean(System
			.getProperty("eclipse.filesystem.useNatives.modificationTimestampMillisecondsResolution", "true")); //$NON-NLS-1$ //$NON-NLS-2$
	private static final boolean USE_LINUX_FFM_DIRECTORY_LISTING = Platform.OS.isLinux()
			&& Platform.ARCH_X86_64.equals(Platform.getOSArch());

	private static final class LinuxFfm {
		private static final StructLayout ERRNO_CAPTURE_LAYOUT = Linker.Option.captureStateLayout();
		private static final VarHandle ERRNO_HANDLE = ERRNO_CAPTURE_LAYOUT.varHandle(groupElement("errno")); //$NON-NLS-1$
		private static final MethodHandle FSTATAT_HANDLE = Linker.nativeLinker().downcallHandle( //
				LinuxDirent.fstatat$address(), LinuxDirent.fstatat$descriptor(), //
				Linker.Option.captureCallState("errno")); //$NON-NLS-1$

		private static int fstatat(int dirfd, MemorySegment pathname, MemorySegment statbuf, int flags,
				MemorySegment capturedErrno) {
			try {
				return (int) FSTATAT_HANDLE.invokeExact(capturedErrno, dirfd, pathname, statbuf, flags);
			} catch (Error | RuntimeException e) {
				throw e;
			} catch (Throwable e) {
				throw new AssertionError("should not reach here", e); //$NON-NLS-1$
			}
		}

		private static int errno(MemorySegment capturedErrno) {
			return (int) ERRNO_HANDLE.get(capturedErrno, 0L);
		}
	}

	@Override
	public FileInfo fetchFileInfo(String fileName) {
		Path path = Paths.get(fileName);
		FileInfo info = new FileInfo();

		// Fill in the name of the file.
		// If the file system is case insensitive, we don't know the real name of the file.
		// Since obtaining the real name in such situation is pretty expensive, we use the name
		// passed as a parameter, which may differ by case from the real name of the file
		// if the file system is case insensitive.
		Path fileNamePath = path.getFileName();
		info.setName(fileNamePath == null ? "" : fileNamePath.toString()); //$NON-NLS-1$

		try {
			PosixFileAttributes attrs = Files.readAttributes(path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

			if (attrs.isSymbolicLink()) {
				info.setAttribute(EFS.ATTRIBUTE_SYMLINK, true);
				info.setStringAttribute(EFS.ATTRIBUTE_LINK_TARGET, Files.readSymbolicLink(path).toString());
				attrs = Files.readAttributes(path, PosixFileAttributes.class);
			}

			info.setExists(true);
			info.setLastModified(attrs.lastModifiedTime().toMillis());
			info.setLength(attrs.size());
			info.setDirectory(attrs.isDirectory());

			Set<PosixFilePermission> perms = attrs.permissions();
			info.setAttribute(EFS.ATTRIBUTE_OWNER_READ, perms.contains(PosixFilePermission.OWNER_READ));
			info.setAttribute(EFS.ATTRIBUTE_OWNER_WRITE, perms.contains(PosixFilePermission.OWNER_WRITE));
			info.setAttribute(EFS.ATTRIBUTE_OWNER_EXECUTE, perms.contains(PosixFilePermission.OWNER_EXECUTE));
			info.setAttribute(EFS.ATTRIBUTE_GROUP_READ, perms.contains(PosixFilePermission.GROUP_READ));
			info.setAttribute(EFS.ATTRIBUTE_GROUP_WRITE, perms.contains(PosixFilePermission.GROUP_WRITE));
			info.setAttribute(EFS.ATTRIBUTE_GROUP_EXECUTE, perms.contains(PosixFilePermission.GROUP_EXECUTE));
			info.setAttribute(EFS.ATTRIBUTE_OTHER_READ, perms.contains(PosixFilePermission.OTHERS_READ));
			info.setAttribute(EFS.ATTRIBUTE_OTHER_WRITE, perms.contains(PosixFilePermission.OTHERS_WRITE));
			info.setAttribute(EFS.ATTRIBUTE_OTHER_EXECUTE, perms.contains(PosixFilePermission.OTHERS_EXECUTE));
		} catch (NoSuchFileException e) {
			// A non-existing file is not considered an error.
		} catch (IOException e) {
			// Leave alone and continue.
			info.setError(IFileInfo.IO_ERROR);
		}
		return info;
	}

	@Override
	public int getSupportedAttributes() {
		return ATTRIBUTES;
	}

	@Override
	public String[] listDirectoryNames(String fileName) {
		if (!USE_LINUX_FFM_DIRECTORY_LISTING) {
			return super.listDirectoryNames(fileName);
		}
		try {
			return listDirectoryNamesLinux(fileName);
		} catch (RuntimeException | LinkageError e) {
			return super.listDirectoryNames(fileName);
		}
	}

	@Override
	public IFileInfo[] listDirectoryAndGetFileInfos(String fileName) {
		if (!USE_LINUX_FFM_DIRECTORY_LISTING) {
			return super.listDirectoryAndGetFileInfos(fileName);
		}
		try {
			return listDirectoryAndGetFileInfosLinux(fileName);
		} catch (RuntimeException | LinkageError e) {
			return super.listDirectoryAndGetFileInfos(fileName);
		}
	}

	private static String[] listDirectoryNamesLinux(String fileName) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment path = allocatePlatformString(fileName, arena);
			MemorySegment directory = LinuxDirent.opendir(path);
			if (MemorySegment.NULL.equals(directory)) {
				return EMPTY_STRING_ARRAY;
			}
			try {
				ArrayList<String> names = new ArrayList<>();
				for (;;) {
					MemorySegment entry = LinuxDirent.readdir(directory);
					if (MemorySegment.NULL.equals(entry)) {
						return names.toArray(String[]::new);
					}
					String name = readDirectoryEntryName(entry);
					if (!isSelfOrParent(name)) {
						names.add(name);
					}
				}
			} finally {
				LinuxDirent.closedir(directory);
			}
		}
	}

	private static IFileInfo[] listDirectoryAndGetFileInfosLinux(String fileName) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment path = allocatePlatformString(fileName, arena);
			MemorySegment directory = LinuxDirent.opendir(path);
			if (MemorySegment.NULL.equals(directory)) {
				return new IFileInfo[0];
			}
			try {
				int directoryFd = LinuxDirent.dirfd(directory);
				if (directoryFd == -1) {
					return new IFileInfo[0];
				}
				MemorySegment statBuffer = arena.allocate(stat.layout());
				MemorySegment capturedErrno = arena.allocate(LinuxFfm.ERRNO_CAPTURE_LAYOUT);
				MemorySegment linkBuffer = arena.allocate(LinuxDirent.PATH_MAX() + 1L);
				ArrayList<IFileInfo> infos = new ArrayList<>();
				for (;;) {
					MemorySegment entry = LinuxDirent.readdir(directory);
					if (MemorySegment.NULL.equals(entry)) {
						return infos.toArray(IFileInfo[]::new);
					}
					MemorySegment entryName = dirent.d_name(entry);
					String name = readCString(entryName);
					if (isSelfOrParent(name)) {
						continue;
					}
					infos.add(readDirectoryEntryInfo(directoryFd, entryName, name, statBuffer, capturedErrno, linkBuffer));
				}
			} finally {
				LinuxDirent.closedir(directory);
			}
		}
	}

	private static IFileInfo readDirectoryEntryInfo(int directoryFd, MemorySegment entryName, String name,
			MemorySegment statBuffer, MemorySegment capturedErrno, MemorySegment linkBuffer) {
		int errno = 0;
		byte[] linkTarget = null;
		if (LinuxFfm.fstatat(directoryFd, entryName, statBuffer, LinuxDirent.AT_SYMLINK_NOFOLLOW(), capturedErrno) != 0) {
			statBuffer.fill((byte) 0);
			errno = LinuxFfm.errno(capturedErrno);
		} else if (isSymbolicLink(statBuffer)) {
			errno = 0;
			linkTarget = readLinkTarget(directoryFd, entryName, linkBuffer);
			if (LinuxFfm.fstatat(directoryFd, entryName, statBuffer, 0, capturedErrno) != 0) {
				statBuffer.fill((byte) 0);
				errno = LinuxFfm.errno(capturedErrno);
			}
		}
		return toFileInfo(name, statBuffer, errno, linkTarget);
	}

	private static byte[] readLinkTarget(int directoryFd, MemorySegment entryName, MemorySegment linkBuffer) {
		long linkPathLen = LinuxDirent.readlinkat(directoryFd, entryName, linkBuffer, LinuxDirent.PATH_MAX());
		if (linkPathLen < 0) {
			return new byte[0];
		}
		linkBuffer.set(JAVA_BYTE, linkPathLen, (byte) 0);
		return linkBuffer.asSlice(0, linkPathLen).toArray(JAVA_BYTE);
	}

	private static FileInfo toFileInfo(String name, MemorySegment statBuffer, int errno, byte[] linkTarget) {
		FileInfo info = new FileInfo();
		info.setName(name);
		if (errno != 0 && errno != LinuxDirent.ENOENT()) {
			info.setError(IFileInfo.IO_ERROR);
			return info;
		}
		info.setExists(errno != LinuxDirent.ENOENT());
		info.setLength(stat.st_size(statBuffer));
		long lastModified = timespec.tv_sec(stat.st_mtim(statBuffer)) * 1_000L;
		if (USE_MILLISECOND_RESOLUTION) {
			lastModified += timespec.tv_nsec(stat.st_mtim(statBuffer)) / 1_000_000L;
		}
		info.setLastModified(lastModified);
		if ((stat.st_mode(statBuffer) & LinuxDirent.S_IFMT()) == LinuxDirent.S_IFDIR()) {
			info.setDirectory(true);
		}
		if (linkTarget != null) {
			info.setAttribute(EFS.ATTRIBUTE_SYMLINK, true);
			if (linkTarget.length > 0) {
				info.setStringAttribute(EFS.ATTRIBUTE_LINK_TARGET, Convert.fromPlatformBytes(linkTarget, linkTarget.length));
			}
		}
		setIfUnset(info, EFS.ATTRIBUTE_OWNER_READ, (stat.st_mode(statBuffer) & LinuxDirent.S_IRUSR()) == 0);
		setIfUnset(info, EFS.ATTRIBUTE_OWNER_WRITE, (stat.st_mode(statBuffer) & LinuxDirent.S_IWUSR()) == 0);
		setIfSet(info, EFS.ATTRIBUTE_OWNER_EXECUTE, (stat.st_mode(statBuffer) & LinuxDirent.S_IXUSR()) != 0);
		setIfSet(info, EFS.ATTRIBUTE_GROUP_READ, (stat.st_mode(statBuffer) & LinuxDirent.S_IRGRP()) != 0);
		setIfSet(info, EFS.ATTRIBUTE_GROUP_WRITE, (stat.st_mode(statBuffer) & LinuxDirent.S_IWGRP()) != 0);
		setIfSet(info, EFS.ATTRIBUTE_GROUP_EXECUTE, (stat.st_mode(statBuffer) & LinuxDirent.S_IXGRP()) != 0);
		setIfSet(info, EFS.ATTRIBUTE_OTHER_READ, (stat.st_mode(statBuffer) & LinuxDirent.S_IROTH()) != 0);
		setIfSet(info, EFS.ATTRIBUTE_OTHER_WRITE, (stat.st_mode(statBuffer) & LinuxDirent.S_IWOTH()) != 0);
		setIfSet(info, EFS.ATTRIBUTE_OTHER_EXECUTE, (stat.st_mode(statBuffer) & LinuxDirent.S_IXOTH()) != 0);
		return info;
	}

	private static void setIfSet(FileInfo info, int attribute, boolean value) {
		if (value) {
			info.setAttribute(attribute, true);
		}
	}

	private static void setIfUnset(FileInfo info, int attribute, boolean unset) {
		if (unset) {
			info.setAttribute(attribute, false);
		}
	}

	private static boolean isSymbolicLink(MemorySegment statBuffer) {
		return (stat.st_mode(statBuffer) & LinuxDirent.S_IFMT()) == LinuxDirent.S_IFLNK();
	}

	private static boolean isSelfOrParent(String name) {
		return ".".equals(name) || "..".equals(name); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String readDirectoryEntryName(MemorySegment entry) {
		return readCString(dirent.d_name(entry));
	}

	private static String readCString(MemorySegment bytes) {
		int length = 0;
		while (length < bytes.byteSize() && bytes.get(JAVA_BYTE, length) != 0) {
			length++;
		}
		byte[] raw = bytes.asSlice(0, length).toArray(JAVA_BYTE);
		return Convert.fromPlatformBytes(raw, raw.length);
	}

	private static MemorySegment allocatePlatformString(String value, Arena arena) {
		byte[] bytes = Convert.toPlatformBytes(value);
		MemorySegment segment = arena.allocate(bytes.length + 1L);
		segment.asSlice(0, bytes.length).copyFrom(MemorySegment.ofArray(bytes));
		segment.set(JAVA_BYTE, bytes.length, (byte) 0);
		return segment;
	}

	@Override
	public boolean putFileInfo(String fileName, IFileInfo info, int options) {
		Path path = Paths.get(fileName);
		Set<PosixFilePermission> perms = new HashSet<>();

		if (info.getAttribute(EFS.ATTRIBUTE_OWNER_READ)) {
			perms.add(PosixFilePermission.OWNER_READ);
		}
		if (info.getAttribute(EFS.ATTRIBUTE_OWNER_WRITE)) {
			perms.add(PosixFilePermission.OWNER_WRITE);
		}
		if (info.getAttribute(EFS.ATTRIBUTE_OWNER_EXECUTE)) {
			perms.add(PosixFilePermission.OWNER_EXECUTE);
		}
		if (info.getAttribute(EFS.ATTRIBUTE_GROUP_READ)) {
			perms.add(PosixFilePermission.GROUP_READ);
		}
		if (info.getAttribute(EFS.ATTRIBUTE_GROUP_WRITE)) {
			perms.add(PosixFilePermission.GROUP_WRITE);
		}
		if (info.getAttribute(EFS.ATTRIBUTE_GROUP_EXECUTE)) {
			perms.add(PosixFilePermission.GROUP_EXECUTE);
		}
		if (info.getAttribute(EFS.ATTRIBUTE_OTHER_READ)) {
			perms.add(PosixFilePermission.OTHERS_READ);
		}
		if (info.getAttribute(EFS.ATTRIBUTE_OTHER_WRITE)) {
			perms.add(PosixFilePermission.OTHERS_WRITE);
		}
		if (info.getAttribute(EFS.ATTRIBUTE_OTHER_EXECUTE)) {
			perms.add(PosixFilePermission.OTHERS_EXECUTE);
		}

		PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
		try {
			view.setPermissions(perms);
		} catch (IOException e) {
			return false;
		}
		return true;
	}
}
