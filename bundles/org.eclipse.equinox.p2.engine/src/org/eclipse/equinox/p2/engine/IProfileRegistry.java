/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.engine;

import java.util.Map;

/**
 * This encapsulates the access to the profile registry. 
 * It deals with persistence in a transparent way.
 */
public interface IProfileRegistry {
	public static final String SELF = "_SELF_"; //$NON-NLS-1$

	/**
	 * Return the profile in the registry that has the given id. If it does not exist, 
	 * then return <code>null</code>.
	 * 
	 * @param id the profile identifier
	 * @return the profile or <code>null</code>
	 */
	IProfile getProfile(String id);

	/**
	 * Return an array of profiles known to this registry. If there are none, then
	 * return an empty array.
	 * 
	 * @return the array of profiles
	 */
	IProfile[] getProfiles();

	//	void addProfile(Profile toAdd) throws IllegalArgumentException;

	void addProfile(String id);

	void addProfile(String id, Map properties);

	void addProfile(String id, Map properties, String parentId);

	/**
	 * Remove the given profile from this profile registry.
	 * 
	 * @param id the profile to remove
	 */
	void removeProfile(String id);
}
