/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.ui.mapping;

import org.eclipse.compare.IStreamMerger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.team.ui.operations.MergeStatus;

/**
 * A special status that is returned when the return code 
 * of the <code>merge</code> method is <code>CONFLICTS</code>.
 * It is possible that there were problems that caused the 
 * auto-merge to fail. In that case, the implementor of
 * <code>IResourceMappingMerger</code> can return a multi-status
 * in which one of the children is a <code>MergeStatus</code> and
 * the others describe other problems that were encountered.
 * <p>
 * This interface is not intended to be implemented by clients.
 * Clients can instead us the {@link MergeStatus} class.
 * 
 * <p>
 * <strong>EXPERIMENTAL</strong>. This class or interface has been added as
 * part of a work in progress. There is a guarantee neither that this API will
 * work nor that it will remain the same. Please do not use this API without
 * consulting with the Platform/Team team.
 * </p>
 * 
 * @see org.eclipse.team.ui.mapping.IResourceMappingMerger
 * @see MergeStatus
 * 
 * @since 3.2
 */
public interface IMergeStatus extends IStatus {

	/**
	 * Indicates that a change conflict prevented some or all of the resource
	 * mappings to be merged (value <code>1</code>). When this code is
	 * returned, the status must be of type
	 * <code>MergeStatus</code> and must contain the list of all
	 * resource mappings for which a manual merge is required.
	 */
	public static final int CONFLICTS = IStreamMerger.CONFLICT;

	/**
	 * Status code describing an internal error (value <code>2</code>).
	 * The status return is not required to be of type <code>MergeStatus</code>
	 * for internal errors.
	 */
	public static final int INTERNAL_ERROR = IStreamMerger.INTERNAL_ERROR;

	/**
	 * Returns the set of resource mappings for which an auto-merge was
	 * not performed. If the code of the status is <code>CONFLICTS</code>
	 * the status may contain a set of mappings or files depending
	 * on what method returned the status.
	 * @return the set of resource mappings for which an auto-merge was
	 * not performed.
	 */
	public abstract ResourceMapping[] getConflictingMappings();

	/**
	 * Returns the set of file for which an auto-merge was
	 * not performed. If the code of the status is <code>CONFLICTS</code>
	 * the status may contain a set of mappings or files depending
	 * on what method returned the status.
	 * @return the set of files for which an auto-merge was
	 * not performed.
	 */
	public abstract IFile[] getConflictingFiles();

}