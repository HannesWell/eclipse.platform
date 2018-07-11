/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.tests.runtime;

import junit.framework.*;

public class AllTests extends TestCase {

	public AllTests() {
		super(null);
	}

	public AllTests(String name) {
		super(name);
	}

	public static Test suite() {
		TestSuite suite = new TestSuite(AllTests.class.getName());
		suite.addTestSuite(CoreExceptionTest.class);
		suite.addTestSuite(IAdapterManagerTest.class);
		suite.addTestSuite(IAdapterManagerServiceTest.class);
		suite.addTestSuite(AdapterManagerDynamicTest.class);
		suite.addTestSuite(OperationCanceledExceptionTest.class);
		suite.addTestSuite(PathTest.class);
		suite.addTestSuite(PlatformTest.class);
		suite.addTest(PluginVersionIdentifierTest.suite());
		suite.addTestSuite(SubMonitorTest.class);
		suite.addTestSuite(SubProgressTest.class);
		suite.addTestSuite(SubMonitorSmallTicksTest.class);
		suite.addTestSuite(ProgressMonitorWrapperTest.class);
		suite.addTest(QualifiedNameTest.suite());
		suite.addTestSuite(SafeRunnerTest.class);
		suite.addTest(StatusTest.suite());
		suite.addTestSuite(URIUtilTest.class);
		suite.addTestSuite(URLTest.class);
		return suite;
	}
}
