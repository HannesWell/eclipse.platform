/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFile;
import org.eclipse.team.internal.ccvs.core.ILogEntry;
import org.eclipse.team.internal.ccvs.ui.CVSUIMessages;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;

public class OpenLogEntryAction extends CVSAction {
	/**
	 * Returns the selected remote files
	 */
	protected ILogEntry[] getSelectedLogEntries() {
		ArrayList entries = null;
		IStructuredSelection selection = getSelection();
		if (!selection.isEmpty()) {
			entries = new ArrayList();
			Iterator elements = selection.iterator();
			while (elements.hasNext()) {
				Object next = elements.next();
				if (next instanceof ILogEntry) {
					entries.add(next);
					continue;
				}
				if (next instanceof IAdaptable) {
					IAdaptable a = (IAdaptable) next;
					Object adapter = a.getAdapter(ILogEntry.class);
					if (adapter instanceof ILogEntry) {
						entries.add(adapter);
						continue;
					}
				}
			}
		}
		if (entries != null && !entries.isEmpty()) {
			ILogEntry[] result = new ILogEntry[entries.size()];
			entries.toArray(result);
			return result;
		}
		return new ILogEntry[0];
	}
	/*
	 * @see CVSAction#execute(IAction)
	 */
	public void execute(IAction action) throws InterruptedException, InvocationTargetException {
		run(new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException {
				final ILogEntry[] entries = getSelectedLogEntries();
				for (int i = 0; i < entries.length; i++) {
					if (entries[i].isDeletion()) {
						MessageDialog.openError(getShell(), CVSUIMessages.OpenLogEntryAction_deletedTitle, CVSUIMessages.OpenLogEntryAction_deleted); // 
					} else {
						ICVSRemoteFile file = entries[i].getRemoteFile();
                        CVSUIPlugin.getPlugin().openEditor(file, monitor);
					}
				}
			}
		}, false, PROGRESS_BUSYCURSOR); 
	}
	/*
	 * @see TeamAction#isEnabled()
	 */
	protected boolean isEnabled() throws TeamException {
		ILogEntry[] entries = getSelectedLogEntries();
		if (entries.length == 0) return false;
		return true;
	}
}
