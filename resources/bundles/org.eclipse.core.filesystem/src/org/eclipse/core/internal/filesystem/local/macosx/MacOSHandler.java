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
import java.nio.file.Path;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.provider.FileInfo;
import org.eclipse.core.internal.filesystem.local.NativeHandler;
import org.eclipse.core.internal.filesystem.local.nio.PosixHandler;

/**
 * macOS handler that keeps ordinary POSIX permission handling in {@link PosixHandler}
 * and adds BSD immutable-flag support so {@link EFS#ATTRIBUTE_READ_ONLY} continues
 * to round-trip through {@link EFS#ATTRIBUTE_IMMUTABLE} on supported macOS systems.
 */
public class MacOSHandler extends NativeHandler {
	private static final int[] POSIX_PERMISSION_ATTRIBUTES = { EFS.ATTRIBUTE_OWNER_READ, EFS.ATTRIBUTE_OWNER_WRITE,
			EFS.ATTRIBUTE_OWNER_EXECUTE, EFS.ATTRIBUTE_GROUP_READ, EFS.ATTRIBUTE_GROUP_WRITE, EFS.ATTRIBUTE_GROUP_EXECUTE,
			EFS.ATTRIBUTE_OTHER_READ, EFS.ATTRIBUTE_OTHER_WRITE, EFS.ATTRIBUTE_OTHER_EXECUTE };

	private final PosixHandler posixHandler = new PosixHandler();

	public static boolean isSupported() {
		return MacFileFlags.isSupported();
	}

	@Override
	public int getSupportedAttributes() {
		return posixHandler.getSupportedAttributes() | EFS.ATTRIBUTE_IMMUTABLE;
	}

	@Override
	public FileInfo fetchFileInfo(String fileName) {
		FileInfo info = posixHandler.fetchFileInfo(fileName);
		if (!info.exists()) {
			return info;
		}
		try {
			info.setAttribute(EFS.ATTRIBUTE_IMMUTABLE, MacFileFlags.isImmutable(MacFileFlags.read(Path.of(fileName))));
		} catch (IOException e) {
			info.setError(IFileInfo.IO_ERROR);
		}
		return info;
	}

	@Override
	public boolean putFileInfo(String fileName, IFileInfo info, int options) {
		if ((options & EFS.SET_ATTRIBUTES) == 0) {
			return true;
		}
		Path path = Path.of(fileName);
		try {
			FileInfo currentInfo = posixHandler.fetchFileInfo(fileName);
			int currentFlags = MacFileFlags.read(path);
			boolean currentImmutable = MacFileFlags.isImmutable(currentFlags);
			boolean immutable = currentImmutable;
			boolean currentReadOnly = currentInfo.getAttribute(EFS.ATTRIBUTE_READ_ONLY);
			boolean requestedReadOnly = info.getAttribute(EFS.ATTRIBUTE_READ_ONLY);
			boolean requestedImmutable = info.getAttribute(EFS.ATTRIBUTE_IMMUTABLE);
			boolean readOnlyChanged = requestedReadOnly != currentReadOnly;
			boolean posixPermissionsChanged = hasPosixPermissionChanges(currentInfo, info);
			if (currentImmutable && !MacFileFlags.hasUserImmutable(currentFlags) && (readOnlyChanged || posixPermissionsChanged)) {
				return false;
			}
			if (readOnlyChanged) {
				immutable = requestedReadOnly;
			} else if (!posixPermissionsChanged && requestedImmutable != currentImmutable) {
				immutable = requestedImmutable;
			}
			int writableFlags = MacFileFlags.withUserImmutable(currentFlags, false);
			if (MacFileFlags.hasUserImmutable(currentFlags)) {
				MacFileFlags.write(path, writableFlags);
			}
			if (!posixHandler.putFileInfo(fileName, info, options)) {
				rollback(path, fileName, currentInfo, options, currentFlags, writableFlags,
						new IOException("Failed to update POSIX attributes")); //$NON-NLS-1$
				return false;
			}
			int updatedFlags = MacFileFlags.read(path);
			int desiredFlags = MacFileFlags.withUserImmutable(updatedFlags, immutable);
			if (desiredFlags != updatedFlags) {
				try {
					MacFileFlags.write(path, desiredFlags);
				} catch (IOException e) {
					rollback(path, fileName, currentInfo, options, currentFlags, writableFlags, e);
					return false;
				}
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private void rollback(Path path, String fileName, FileInfo currentInfo, int options, int currentFlags, int writableFlags,
			IOException failure) {
		if (!posixHandler.putFileInfo(fileName, currentInfo, options)) {
			failure.addSuppressed(new IOException("Failed to restore POSIX attributes")); //$NON-NLS-1$
		}
		if (writableFlags != currentFlags) {
			try {
				MacFileFlags.write(path, currentFlags);
			} catch (IOException suppressed) {
				failure.addSuppressed(suppressed);
			}
		}
	}

	private static boolean hasPosixPermissionChanges(IFileInfo currentInfo, IFileInfo requestedInfo) {
		for (int attribute : POSIX_PERMISSION_ATTRIBUTES) {
			if (currentInfo.getAttribute(attribute) != requestedInfo.getAttribute(attribute)) {
				return true;
			}
		}
		return false;
	}
}
