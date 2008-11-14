/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.tests.touchpoint.eclipse;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Properties;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.equinox.internal.p2.core.helpers.ServiceHelper;
import org.eclipse.equinox.internal.p2.touchpoint.eclipse.Activator;
import org.eclipse.equinox.internal.p2.touchpoint.eclipse.Util;
import org.eclipse.equinox.internal.provisional.p2.core.location.AgentLocation;
import org.eclipse.equinox.internal.provisional.p2.engine.IProfile;
import org.eclipse.equinox.internal.provisional.p2.metadata.MetadataFactory;
import org.eclipse.equinox.internal.provisional.p2.metadata.TouchpointData;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;

/**
 * @since 1.0
 */
public class UtilTest extends AbstractProvisioningTest {
	/*
	 * Constructor for the class.
	 */
	public UtilTest(String name) {
		super(name);
	}

	/*
	 * Run all the tests in this class.
	 */
	public static Test suite() {
		return new TestSuite(UtilTest.class);
	}

	public void testDefaultBundlePool() throws MalformedURLException {
		IProfile profile = createProfile("test");
		AgentLocation agentLocation = (AgentLocation) ServiceHelper.getService(Activator.getContext(), AgentLocation.class.getName());
		try {
			assertEquals(URIUtil.toURI(agentLocation.getDataArea("org.eclipse.equinox.p2.touchpoint.eclipse")), Util.getBundlePoolLocation(profile));
		} catch (URISyntaxException e) {
			fail("0.99", e);
		}
	}

	public void testExplicitBundlePool() throws MalformedURLException {
		Properties props = new Properties();
		File cacheDir = new File(System.getProperty("java.io.tmpdir"), "cache");
		props.put(IProfile.PROP_CACHE, cacheDir.toString());
		IProfile profile = createProfile("test", null, props);
		assertEquals(cacheDir.toURL().toExternalForm(), Util.getBundlePoolLocation(profile).toString());
	}

	public void testMissingManifest() {
		TouchpointData emptyData = MetadataFactory.createTouchpointData(Collections.EMPTY_MAP);
		assertNull(Util.getManifest(new TouchpointData[] {emptyData}));
	}
}
