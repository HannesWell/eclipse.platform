package org.eclipse.debug.ui;

import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jface.operation.IRunnableContext;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
 /**
  * A launch configuraiton dialog is used to edit and launch
  * launch configurations. It contains a launch configuration
  * tab group.
  * 
  * @see ILaunchConfigurationTabGroup
  * @see ILaunchConfigurationTab
  * @since 2.0
  */

public interface ILaunchConfigurationDialog extends IRunnableContext {
	
	/**
	 * Return value from <code>open()</code> method of a
	 * launch configuration dialog when a launch completed
	 * successfully with a single lick (i.e. without opening a
	 * launch configuration dialog).
	 */
	public static final int SINGLE_CLICK_LAUNCHED = 2;
			
	/**
	 * Adjusts the enable state of the Launch button
	 * to reflect the state of the active tab group.
	 * <p>
	 * This may be called by to force a button state
	 * update.
	 * </p>
	 */
	public void updateButtons();
	
	/**
	 * Updates the message (or error message) shown in the message line to 
	 * reflect the state of the currently active tab in this launch
	 * configuration dialog.
	 * <p>
	 * This method may be called to force a message 
	 * update.
	 * </p>
	 */
	public void updateMessage();
	
	/**
	 * Sets the contents of the name field to the given name.
	 * 
	 * @param name new name value
	 */ 
	public void setName(String name);
	
	/**
	 * Returns a unique launch configuration name, using the given name
	 * as a seed.
	 * 
	 * @param name seed from which to generate a new unique name
	 */ 
	public String generateName(String name);
	
	/**
	 * Returns the tabs currently being displayed, or
	 * <code>null</code> if none.
	 * 
	 * @return currently displayed tabs, or <code>null</code>
	 */
	public ILaunchConfigurationTab[] getTabs();
	
	/**
	 * Returns the mode in which this dialog was opened -
	 * run or debug.
	 * 
	 * @return one of <code>RUN_MODE</code> or <code>DEBUG_MODE</code>
	 * @see ILaunchManager
	 */
	public String getMode();		
		
}
