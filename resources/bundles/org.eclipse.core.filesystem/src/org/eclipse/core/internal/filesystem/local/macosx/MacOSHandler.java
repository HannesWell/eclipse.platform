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

public class MacOSHandler extends NativeHandler {
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
		Path path = Path.of(fileName);
		boolean immutable = info.getAttribute(EFS.ATTRIBUTE_IMMUTABLE);
		try {
			int currentFlags = MacFileFlags.read(path);
			int writableFlags = MacFileFlags.withUserImmutable(currentFlags, false);
			if (MacFileFlags.hasUserImmutable(currentFlags)) {
				MacFileFlags.write(path, writableFlags);
			}
			if (!posixHandler.putFileInfo(fileName, info, options)) {
				if (writableFlags != currentFlags) {
					MacFileFlags.write(path, currentFlags);
				}
				return false;
			}
			int desiredFlags = MacFileFlags.withUserImmutable(currentFlags, immutable);
			if (desiredFlags != writableFlags) {
				MacFileFlags.write(path, desiredFlags);
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
