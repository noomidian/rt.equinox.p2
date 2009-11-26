/*******************************************************************************
 * Copyright (c) 2007, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     EclipseSource - ongoing development
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.ui;

import java.net.URI;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.equinox.internal.p2.artifact.repository.ArtifactRepositoryManager;
import org.eclipse.equinox.internal.provisional.p2.core.ProvisionException;
import org.eclipse.equinox.internal.provisional.p2.repository.IRepository;
import org.eclipse.equinox.internal.provisional.p2.repository.IRepositoryManager;
import org.eclipse.equinox.p2.operations.RepositoryTracker;
import org.eclipse.equinox.p2.ui.ProvisioningUI;

/**
 * An object that queries a particular set of artifact repositories.
 */
public class QueryableArtifactRepositoryManager extends QueryableRepositoryManager {

	public QueryableArtifactRepositoryManager(ProvisioningUI ui, boolean includeDisabledRepos) {
		super(ui, includeDisabledRepos);
	}

	protected IRepositoryManager getRepositoryManager() {
		return getSession().getArtifactRepositoryManager();
	}

	protected IRepository doLoadRepository(IRepositoryManager manager, URI location, IProgressMonitor monitor) throws ProvisionException {
		return ui.loadArtifactRepository(location, false, monitor);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.p2.ui.QueryableRepositoryManager#getRepositoryFlags(org.eclipse.equinox.p2.ui.RepositoryManipulator)
	 */
	protected int getRepositoryFlags(RepositoryTracker repositoryManipulator) {
		return repositoryManipulator.getArtifactRepositoryFlags();
	}

	protected IRepository getRepository(IRepositoryManager manager, URI location) {
		// note the use of ArtifactRepositoryManager (the concrete implementation).
		if (manager instanceof ArtifactRepositoryManager) {
			return ((ArtifactRepositoryManager) manager).getRepository(location);
		}
		return null;
	}
}
