/*******************************************************************************
 * Copyright (c) 2007, 2019 IBM Corporation and others.
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
 *******************************************************************************/
package org.eclipse.jsch.internal.core;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

public class PreferenceModifyListener extends
    org.eclipse.core.runtime.preferences.PreferenceModifyListener{

  public PreferenceModifyListener(){
    // Nothing to do
  }

  @Override
  public IEclipsePreferences preApply(IEclipsePreferences node){
    // the node does not need to be the root of the hierarchy
    Preferences root=node.node("/"); //$NON-NLS-1$
    try{
      // we must not create empty preference nodes, so first check if the node exists
      if(root.nodeExists(InstanceScope.SCOPE)){
        Utils.migrateSSH2Preferences(root.node(InstanceScope.SCOPE));
      }
    }
    catch(BackingStoreException e){
      // do nothing
    }
    return super.preApply(node);
  }

}
