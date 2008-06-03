/*******************************************************************************
 * Copyright (c) 2006, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.examples.model.mapping;

import java.util.*;

import org.eclipse.core.resources.*;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.resources.mapping.ResourceTraversal;
import org.eclipse.core.runtime.*;
import org.eclipse.team.core.diff.*;
import org.eclipse.team.core.history.IFileRevision;
import org.eclipse.team.core.mapping.*;
import org.eclipse.team.core.mapping.provider.*;
import org.eclipse.team.examples.filesystem.FileSystemPlugin;
import org.eclipse.team.examples.model.*;

/**
 * A resource mapping merger for our example model 
 */
public class ModelMerger extends ResourceMappingMerger {

	private final org.eclipse.team.examples.model.mapping.ExampleModelProvider provider;

	public ModelMerger(org.eclipse.team.examples.model.mapping.ExampleModelProvider provider) {
		this.provider = provider;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.mapping.ResourceMappingMerger#getModelProvider()
	 */
	protected org.eclipse.core.resources.mapping.ModelProvider getModelProvider() {
		return provider;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.core.mapping.ResourceMappingMerger#merge(org.eclipse.team.core.mapping.IMergeContext, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public IStatus merge(IMergeContext mergeContext, IProgressMonitor monitor) throws CoreException {
		try {
			IStatus status;
			// Only override the merge for three-way synchronizations
			if (mergeContext.getType() == SynchronizationContext.THREE_WAY) {
				monitor.beginTask("Merging model elements", 100);
				status = mergeModelElements(mergeContext, new SubProgressMonitor(monitor, 50));
				// Stop the merge if there was a failure
				if (!status.isOK())
					return status;
				// We need to wait for any background processing to complete for the context
				// so the diff tree will be up-to-date when we delegate the rest of the merge
				// to the superclass
				try {
					Platform.getJobManager().join(mergeContext, new SubProgressMonitor(monitor, 50));
				} catch (InterruptedException e) {
					// Ignore
				}
				// Delegate the rest of the merge to the superclass
				status = super.merge(mergeContext, monitor);
			} else {
				status = super.merge(mergeContext, monitor);
			}
			return status;
		} finally {
			monitor.done();
		}
	}

	/*
	 * Merge all the model element changes in the context
	 */
	private IStatus mergeModelElements(IMergeContext mergeContext, IProgressMonitor monitor) throws CoreException {
		try {
			IDiff[] modeDiffs = getModDiffs(mergeContext);
			List failures = new ArrayList();
			monitor.beginTask(null, 100 * modeDiffs.length);
			for (int i = 0; i < modeDiffs.length; i++) {
				IDiff diff = modeDiffs[i];
				if (!mergeModelElement(mergeContext, diff, new SubProgressMonitor(monitor, 100))) {
					failures.add(diff);
				}
			}
			if (failures.size() > 0) {
				return new MergeStatus(FileSystemPlugin.ID, "Several objects could not be merged", getMappings(failures));
			}
			return Status.OK_STATUS;
		} finally {
			monitor.done();
		}
	}

	private ResourceMapping[] getMappings(List failures) {
		List mappings = new ArrayList();
		for (Iterator iter = failures.iterator(); iter.hasNext();) {
			IDiff diff = (IDiff) iter.next();
			IResource resource = ResourceDiffTree.getResourceFor(diff);
			ModelObjectDefinitionFile file = (ModelObjectDefinitionFile)ModelObject.create(resource);
			mappings.add(file.getAdapter(ResourceMapping.class));
		}
		return (ResourceMapping[]) mappings.toArray(new ResourceMapping[mappings.size()]);
	}

	/*
	 * Return all the diffs for MOD files.
	 */
	private IDiff[] getModDiffs(IMergeContext mergeContext) {
		final List result = new ArrayList();
		mergeContext.getDiffTree().accept(getModelProjectTraversals(mergeContext), new IDiffVisitor() {
			public boolean visit(IDiff diff) {
				IResource resource = ResourceDiffTree.getResourceFor(diff);
				if (ModelObjectDefinitionFile.isModFile(resource)) {
					result.add(diff);
				}
				return true;
			}
		
		});
		return (IDiff[]) result.toArray(new IDiff[result.size()]);
	}

	/*
	 * Return a traversal that covers all the model projects in the scope of the merge.
	 */
	private ResourceTraversal[] getModelProjectTraversals(IMergeContext mergeContext) {
		IProject[] scopeProjects = mergeContext.getScope().getProjects();
		List modelProjects = new ArrayList();
		for (int i = 0; i < scopeProjects.length; i++) {
			IProject project = scopeProjects[i];
			try {
				if (ModelProject.isModProject(project)) {
					modelProjects.add(project);
				}
			} catch (CoreException e) {
				FileSystemPlugin.log(e);
			}
		}
		if (modelProjects.isEmpty())
			return new ResourceTraversal[0];
		return new ResourceTraversal[] { 
			new ResourceTraversal((IResource[]) modelProjects.toArray(new IResource[modelProjects.size()]), 
					IResource.DEPTH_INFINITE, IResource.NONE)	
		};
	}

	/*
	 * Merge the model definition file and all the element files it contains.
	 */
	private boolean mergeModelElement(IMergeContext mergeContext, IDiff diff, IProgressMonitor monitor) throws CoreException {
		if (diff instanceof IThreeWayDiff) {
			IThreeWayDiff twd = (IThreeWayDiff) diff;
			if (twd.getDirection() == IThreeWayDiff.INCOMING
					|| twd.getDirection() == IThreeWayDiff.CONFLICTING) {
				IResource resource = ResourceDiffTree.getResourceFor(diff);
				
				// First, check if a change conflicts with a deletion
				if (twd.getDirection() == IThreeWayDiff.CONFLICTING) {
					if (!resource.exists())
						return false;
					if (((IResourceDiff)twd.getRemoteChange()).getAfterState() == null)
						return false;
				}
				
				// First determine the element files and element file changes
				IResourceDiff remoteChange = (IResourceDiff)twd.getRemoteChange();
				IResource[] localElements = getReferencedResources(resource);
				IResource[] baseElements = getReferencedResources(resource.getProject().getName(), remoteChange.getBeforeState(), monitor);
				IResource[] remoteElements = getReferencedResources(resource.getProject().getName(), remoteChange.getAfterState(), monitor);
				IResource[] addedElements = getAddedElements(baseElements, remoteElements);
				// Trick: The removed elements can be obtained by reversing the base and remote and looking for added
				IResource[] removedElements = getAddedElements(remoteElements, baseElements);
				
				// Check to see if any removed elements have changed locally
				if (hasOutgoingChanges(mergeContext, removedElements)) {
					return false;
				}
				
				// Now try to merge all the element files involved
				Set elementFiles = new HashSet();
				elementFiles.addAll(Arrays.asList(baseElements));
				elementFiles.addAll(Arrays.asList(localElements));
				elementFiles.addAll(Arrays.asList(remoteElements));
				if (!mergeElementFiles(mergeContext, (IResource[]) elementFiles.toArray(new IResource[elementFiles.size()]), monitor)) {
					return false;
				}
				
				// Finally, merge the model definition
				if (!resource.exists()) {
					// This is a new model definition so just merge it
					IStatus status = mergeContext.merge(diff, false, monitor);
					if (!status.isOK())
						return false;
				} else {
					// Update the contents of the model definition file
					ModelObjectDefinitionFile file = (ModelObjectDefinitionFile)ModelObject.create(resource);
					elementFiles = new HashSet();
					elementFiles.addAll(Arrays.asList(localElements));
					elementFiles.addAll(Arrays.asList(addedElements));
					elementFiles.removeAll(Arrays.asList(removedElements));
					file.setElements((IResource[]) elementFiles.toArray(new IResource[elementFiles.size()]));
					// Let the merge context know we handled the file
					mergeContext.markAsMerged(diff, false, monitor);
				}
			}
		}
		return true;
	}

	private boolean mergeElementFiles(IMergeContext mergeContext, IResource[] resources, IProgressMonitor monitor) throws CoreException {
		IDiff[] diffs = getDiffs(mergeContext, resources);
		IStatus status = mergeContext.merge(diffs, false, monitor);
		return status.isOK();
	}

	private IDiff[] getDiffs(IMergeContext mergeContext, IResource[] resources) {
		Set diffSet = new HashSet();
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			IDiff[] diffs = mergeContext.getDiffTree().getDiffs(resource, IResource.DEPTH_ZERO);
			diffSet.addAll(Arrays.asList(diffs));
		}
		return (IDiff[]) diffSet.toArray(new IDiff[diffSet.size()]);
	}

	private boolean hasOutgoingChanges(IMergeContext mergeContext, IResource[] removedElements) {
		FastDiffFilter fastDiffFilter = new FastDiffFilter() {
			public boolean select(IDiff diff) {
				if (diff instanceof IThreeWayDiff) {
					IThreeWayDiff twd = (IThreeWayDiff) diff;
					return twd.getDirection() == IThreeWayDiff.OUTGOING || twd.getDirection() == IThreeWayDiff.CONFLICTING;
				}
				return false;
			}
		};
		for (int i = 0; i < removedElements.length; i++) {
			IResource resource = removedElements[i];
			if  (mergeContext.getDiffTree().hasMatchingDiffs(resource.getFullPath(), fastDiffFilter))
				return true;	
		}
		return false;
	}

	private IResource[] getAddedElements(IResource[] baseElements, IResource[] remoteElements) {
		List result = new ArrayList();
		Set base = new HashSet();
		for (int i = 0; i < baseElements.length; i++) {
			IResource resource = baseElements[i];
			base.add(resource);
		}
		for (int i = 0; i < remoteElements.length; i++) {
			IResource resource = remoteElements[i];
			if (!base.contains(resource))
				result.add(resource);
		}
		return (IResource[]) result.toArray(new IResource[result.size()]);
	}

	private IResource[] getReferencedResources(IResource resource) throws CoreException {
		if (resource instanceof IFile && resource.exists()) {
			return ModelObjectDefinitionFile.getReferencedResources(resource.getProject().getName(), (IFile) resource);
		}
		return new IResource[0];
	}
	
	private IResource[] getReferencedResources(String projectName, IFileRevision revision, IProgressMonitor monitor) throws CoreException {
		if (revision != null) {
			return ModelObjectDefinitionFile.getReferencedResources(projectName, revision.getStorage(monitor));
		} 
		return new IResource[0];
	}

}
