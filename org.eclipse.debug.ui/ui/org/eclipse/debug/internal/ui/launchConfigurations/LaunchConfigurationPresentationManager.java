/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.launchConfigurations;

 
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchDelegate;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.ILaunchMode;
import org.eclipse.debug.internal.core.IConfigurationElementConstants;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.LaunchConfigurationTabExtension;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.debug.ui.ILaunchConfigurationTabGroup;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.activities.IWorkbenchActivitySupport;
import org.eclipse.ui.activities.WorkbenchActivityHelper;

import com.ibm.icu.text.MessageFormat;

/**
 * Manages contributed launch configuration tabs
 */ 
public class LaunchConfigurationPresentationManager {
	
	/**
	 * The singleton launch configuration presentation manager
	 */
	private static LaunchConfigurationPresentationManager fgDefault;
			
	/**
	 * Collection of launch configuration tab group extensions
	 * defined in plug-in xml. Entries are keyed by launch
	 * configuration type identifier (<code>String</code>),
	 * and entires are tables of launch modes (<code>String</code>)
	 * to <code>LaunchConfigurationTabGroupExtension</code>. "*" is
	 * used to represent the default tab group (i.e. unspecified mode).
	 */
	private Hashtable fTabGroupExtensions;	
	
	/**
	 * contributed tabs are stored by the tab group id that they contribute to.
	 * each entry is a futher <code>Hashtable</code> consisting of the corrseponding
	 * <code>LaunchConfigurationTabExtension</code> objects for each contributed tab stored by their 
	 * id
	 * 
	 * @since 3.3
	 */
	private Hashtable fContributedTabs;
			
	/**
	 * Constructs the singleton launch configuration presentation
	 * manager.
	 */
	private LaunchConfigurationPresentationManager() {
		fgDefault = this;
	}

	/**
	 * Returns the launch configuration presentation manager
	 */
	public static LaunchConfigurationPresentationManager getDefault() {
		if (fgDefault == null) {
			fgDefault = new LaunchConfigurationPresentationManager();
		}
		return fgDefault;
	}
		
	/**
	 * Creates launch configuration tab group extensions for each extension
	 * defined in XML, and adds them to the table of tab group extensions.
	 */
	private void initializeTabGroupExtensions() {
		if(fTabGroupExtensions == null) {
			fTabGroupExtensions = new Hashtable();
			IExtensionPoint extensionPoint = Platform.getExtensionRegistry().getExtensionPoint(DebugUIPlugin.getUniqueIdentifier(), IDebugUIConstants.EXTENSION_POINT_LAUNCH_CONFIGURATION_TAB_GROUPS);
			IConfigurationElement[] groups = extensionPoint.getConfigurationElements();
			LaunchConfigurationTabGroupExtension group = null;
			String typeId = null;
			Map map = null;
			List modes = null;
			for (int i = 0; i < groups.length; i++) {
				group = new LaunchConfigurationTabGroupExtension(groups[i]);
				typeId = group.getTypeIdentifier();
				map = (Map)fTabGroupExtensions.get(typeId);
				if (map == null) {
					map = new Hashtable();
					fTabGroupExtensions.put(typeId, map);
				}
				modes = group.getModes();
				if(modes.isEmpty()) {
					map.put("*", group); //$NON-NLS-1$
				}
				for(Iterator iter = modes.iterator(); iter.hasNext();) {
					map.put(iter.next(), group);
				}
			}
		}
	}	
	
	/**
	 * This method is used to collect all of the contributed tabs defined by the <code>launchConfigurationTabs</code>
	 * extension point
	 *
	 * @since 3.3
	 */
	private void initializeContributedTabExtensions() {
		fContributedTabs = new Hashtable();
		IExtensionPoint epoint = Platform.getExtensionRegistry().getExtensionPoint(DebugUIPlugin.getUniqueIdentifier(), IDebugUIConstants.EXTENSION_POINT_LAUNCH_TABS);
		IConfigurationElement[] elements = epoint.getConfigurationElements();
		LaunchConfigurationTabExtension tab = null;
		Hashtable element = null;
		for(int i = 0; i < elements.length; i++) {
			tab = new LaunchConfigurationTabExtension(elements[i]);
			element = (Hashtable) fContributedTabs.get(tab.getTabGroupId());
			if(element == null) {
				element = new Hashtable();
				element.put(tab.getIdentifier(), tab);
				fContributedTabs.put(tab.getTabGroupId(), element);
			}
			element.put(tab.getIdentifier(), tab);
		}
	}
	
	/**
	 * Returns the tab group for the given launch configuration type and mode.
	 * 
	 * @param type launch configuration type
	 * @param mode launch mode
	 * @return the tab group for the given type of launch configuration, or <code>null</code> if none
	 * @exception CoreException if an exception occurs creating the group
	 */
	public ILaunchConfigurationTabGroup getTabGroup(ILaunchConfigurationType type, String mode) throws CoreException {
		HashSet modes = new HashSet();
		modes.add(mode);
		LaunchConfigurationTabGroupExtension ext = getExtension(type.getIdentifier(), modes);
		if (ext == null) {
			IStatus status = new Status(IStatus.ERROR, IDebugUIConstants.PLUGIN_ID, IDebugUIConstants.INTERNAL_ERROR,
			 MessageFormat.format(LaunchConfigurationsMessages.LaunchConfigurationPresentationManager_No_tab_group_defined_for_launch_configuration_type__0__3, (new String[] {type.getIdentifier()})), null);  
			 throw new CoreException(status);
		} 
		return new LaunchConfigurationTabGroupWrapper(ext.newTabGroup(), ext.getIdentifier(), null);		
	}
	
	/**
	 * Returns the tab group for the given launch configutation and the mode the dialog opened in
	 * @param type the type of the configuration
	 * @param config
	 * @param mode
	 * @return
	 * @throws CoreException
	 */
	public ILaunchConfigurationTabGroup getTabGroup(ILaunchConfiguration config, String mode) throws CoreException {
		HashSet modes = new HashSet();
		modes.add(mode);
		LaunchConfigurationTabGroupExtension ext = getExtension(config.getType().getIdentifier(), modes);
		if (ext == null) {
			IStatus status = new Status(IStatus.ERROR, IDebugUIConstants.PLUGIN_ID, IDebugUIConstants.INTERNAL_ERROR,
			 MessageFormat.format(LaunchConfigurationsMessages.LaunchConfigurationPresentationManager_No_tab_group_defined_for_launch_configuration_type__0__3, (new String[] {config.getType().getIdentifier()})), null);  
			 throw new CoreException(status);
		} 
		return new LaunchConfigurationTabGroupWrapper(ext.newTabGroup(), ext.getIdentifier(), config);
	}
	
	/**
	 * Returns the proxy elements for all contributed tabs for the specified tab group id
	 * @param groupid the id of the tab group
	 * @param config the config the tab group is opened on
	 * @param mode the mode the associated launch dialog is opened on
	 * @return the listing of all of the tab extensions or an empty array, never <code>null</code>
	 * 
	 * @since 3.3
	 */
	protected LaunchConfigurationTabExtension[] getTabExtensions(String groupid, ILaunchConfiguration config, String mode) throws CoreException {
		initializeContributedTabExtensions();
		Hashtable tabs = (Hashtable) fContributedTabs.get(groupid);
		if(tabs != null) {
			return filterLaunchTabExtensions((LaunchConfigurationTabExtension[]) tabs.values().toArray(new LaunchConfigurationTabExtension[tabs.size()]), config, mode);
		}
		return new LaunchConfigurationTabExtension[0];
	}
	
	/**
	 * Returns a listing of <code>LaunchConfiguraitonTabExtension</code>s that does not contain any tabs
	 * from disabled activities
	 * <p>
	 * There are thre ways that tabs can be filtered form the launch dialog:
	 * <ol>
	 * <li>The tabs can belong to tooling that is contributed via a specific type of workbench activity, and is therefore filtered with capabilities</li>
	 * <li>The tabs can be filtered via the associatedDelegate extension point, if a tab is said to apply only to certain tooling, only show it in the instance when that tooling is used</li>
	 * <li>A tab is not part of a workbench activity, nor specifies an associated launch delegate -- show the tab</li>
	 * </ol>
	 * </p>
	 * @param tabs the raw listing of tabs to filter
	 * @return the listing of filtered <code>LaunchConfigurationTabExtension</code>s or an empty array, never <code>null</code>
	 * 
	 * @since 3.3
	 */
	protected LaunchConfigurationTabExtension[] filterLaunchTabExtensions(LaunchConfigurationTabExtension[] tabs, ILaunchConfiguration config, String mode) throws CoreException {
		IWorkbenchActivitySupport as = PlatformUI.getWorkbench().getActivitySupport();
		if(as == null || config == null) {
			return tabs;
		}
		HashSet set = new HashSet();
		for(int i = 0; i < tabs.length; i ++) {
		//filter capabilities
			if(!WorkbenchActivityHelper.filterItem(new LaunchTabContribution(tabs[i]))) {
			//filter to preferred delegate (if there is one)
				HashSet modes = (HashSet) config.getModes();
				modes.add(mode);
				ILaunchDelegate delegate = config.getPreferredDelegate(modes);
				if(delegate == null) {
					delegate = config.getType().getPreferredDelegate(modes);
				}
				Set delegateSet = tabs[i].getDelegateSet();
				if(delegate != null) {
					if(delegateSet.isEmpty() || delegateSet.contains(delegate.getId())) {
						set.add(tabs[i]);
					}
				}
				else {
					//otherwise filter based on the collection of delegates for the modes
					ILaunchDelegate[] delegates = config.getType().getDelegates(modes);
					for(int j = 0; j < delegates.length; j++) {
						if(delegateSet.size() == 0 || delegateSet.contains(delegates[j].getId())) {
							//associated with all modes and tab groups or only specific ones if indicated
							set.add(tabs[i]);
						}
					}
				}
			}
		}
		return (LaunchConfigurationTabExtension[]) set.toArray(new LaunchConfigurationTabExtension[set.size()]);
	}
	
	/**
	 * Returns the launch tab group extension for the given type and mode, or
	 * <code>null</code> if none
	 * 
	 * @param type launch configuration type identifier
	 * @param mode launch mode identifier
	 * @return launch tab group extension or <code>null</code>
	 */
	protected LaunchConfigurationTabGroupExtension getExtension(String type, Set modes) {
		initializeTabGroupExtensions();
		Map map = (Map)fTabGroupExtensions.get(type);
		if (map != null) {
			Object extension = map.get(modes);
			if (extension == null) {
				// get the default tabs
				extension = map.get("*"); //$NON-NLS-1$
			}
			return (LaunchConfigurationTabGroupExtension)extension;
		}
		return null;
	}
	
	/**
	 * Returns the identifier of the help context that is associated with the
	 * specified launch configuration type and mode, or <code>null</code> if none.
	 * 
	 * @param type launch config type
	 * @param mode launch mode
	 * @return the identifier for the help context associated with the given
	 * type of launch configuration, or <code>null</code>
	 * @exception CoreException if an exception occurs creating the group
	 * @since 2.1
	 */
	public String getHelpContext(ILaunchConfigurationType type, String mode) throws CoreException {
		HashSet modes = new HashSet();
		modes.add(mode);
		LaunchConfigurationTabGroupExtension ext = getExtension(type.getIdentifier(), modes);
		if (ext == null) {
			IStatus status = new Status(IStatus.ERROR, IDebugUIConstants.PLUGIN_ID, IDebugUIConstants.INTERNAL_ERROR,
			 MessageFormat.format(LaunchConfigurationsMessages.LaunchConfigurationPresentationManager_No_tab_group_defined_for_launch_configuration_type__0__3, (new String[] {type.getIdentifier()})), null); 
			 throw new CoreException(status);
		} 
		return ext.getHelpContextId();		
	}
	
	/**
	 * Returns the description of the given configuration type
	 * in the specified mode or <code>null</code> if none.
	 * 
	 * @param configType the config type
	 * @param mode the launch mode
	 * @return the description of the given configuration type, possible <code>null</code>
	 */
	public String getDescription(ILaunchConfigurationType configType, String mode) {
		HashSet modes = new HashSet();
		modes.add(mode);
		LaunchConfigurationTabGroupExtension extension = getExtension(configType.getAttribute(IConfigurationElementConstants.ID), modes);
		return (extension != null ? extension.getDescription(modes) : null);
	}	
	
	/**
	 * Returns a sorted list of launch mode names corresponding to the given identifiers.
	 * 
	 * @param modes set of launch mode identifiers
	 * @return sorted list of launch mode names
	 */
	public List getLaunchModeNames(Set modes) {
		List names = new ArrayList();
		Iterator iterator = modes.iterator();
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		while (iterator.hasNext()) {
			String id = (String) iterator.next();
			ILaunchMode mode = manager.getLaunchMode(id);
			if (mode == null) {
				names.add(id);
			} else {
				names.add(DebugUIPlugin.removeAccelerators(mode.getLabel()));
			}
		}
		Collections.sort(names);
		return names;
	}
	
	/**
	 * Returns the label of the mode id with all accelerators removed
	 * @param modeid the id of the mode i.e. 'run'
	 * @return the formatted label of the specified mode id with all accelerators removed, or <code>null</code> if no label is available
	 * @since 3.3
	 */
	public String getLaunchModeLabel(String modeid) {
		String mode = null;
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchMode lmode = manager.getLaunchMode(modeid);
		if(lmode != null) {
			return lmode.getLabel();
		}
		return mode;
	}
	
}

