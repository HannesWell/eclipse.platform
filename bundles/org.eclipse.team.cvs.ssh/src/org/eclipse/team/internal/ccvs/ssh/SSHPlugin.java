package org.eclipse.team.internal.ccvs.ssh;/* * (c) Copyright IBM Corp. 2000, 2002. * All Rights Reserved. */import org.eclipse.core.runtime.CoreException;import org.eclipse.core.runtime.IPluginDescriptor;import org.eclipse.core.runtime.Plugin;public class SSHPlugin extends Plugin {		public static String ID = "org.eclipse.team.cvs.ssh"; //$NON-NLS-1$	private static SSHPlugin instance;		/**	 * Constructor for SSHPlugin	 */	public SSHPlugin(IPluginDescriptor d) {		super(d);			instance = this;	}		/**	 * @see Plugin#startup()	 */	public void startup() throws CoreException {		super.startup();		Policy.localize("org.eclipse.team.internal.ccvs.ssh.messages"); //$NON-NLS-1$	}		/**	 * Method getPlugin.	 */	public static SSHPlugin getPlugin() {		return instance;	}}