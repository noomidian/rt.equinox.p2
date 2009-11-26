/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/

package org.eclipse.equinox.p2.ui;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.internal.p2.core.helpers.LogHelper;
import org.eclipse.equinox.internal.p2.ui.ProvUIActivator;
import org.eclipse.equinox.internal.p2.ui.ProvUIMessages;
import org.eclipse.equinox.internal.provisional.p2.core.ProvisionException;
import org.eclipse.equinox.p2.operations.ProvisioningJob;
import org.eclipse.equinox.p2.operations.RepositoryTracker;
import org.eclipse.ui.statushandlers.StatusManager;

/**
 * A job that loads a set of metadata repositories and caches the loaded repositories.
 * This job can be used when repositories are loaded by a client who wishes to 
 * maintain (and pass along) the in-memory references to the repositories.  For example,
 * repositories can be loaded in the background and then passed to another
 * component, thus ensuring that the repositories remain loaded in memory.
 * 
 * @since 2.0
 * @noextend This class is not intended to be subclassed by clients.
 */
public class LoadMetadataRepositoryJob extends ProvisioningJob {

	/**
	 * An object representing the family of jobs that load repositories.
	 */
	public static final Object LOAD_FAMILY = new Object();

	/**
	 * The key that should be used to set a property on a repository load job to indicate
	 * that authentication should be suppressed when loading the repositories.
	 */
	public static final QualifiedName SUPPRESS_AUTHENTICATION_JOB_MARKER = new QualifiedName(ProvUIActivator.PLUGIN_ID, "SUPPRESS_AUTHENTICATION_REQUESTS"); //$NON-NLS-1$

	/**
	 * The key that should be used to set a property on a repository load job to indicate
	 * that repository events triggered by this job should be suppressed so that clients
	 * will ignore all events related to the load.
	 */
	public static final QualifiedName SUPPRESS_REPOSITORY_EVENTS = new QualifiedName(ProvUIActivator.PLUGIN_ID, "SUPRESS_REPOSITORY_EVENTS"); //$NON-NLS-1$

	/**
	 * The key that should be used to set a property on a repository load job to indicate
	 * that load errors should be accumulated into a single status rather than reported
	 * as they occur.
	 */
	public static final QualifiedName ACCUMULATE_LOAD_ERRORS = new QualifiedName(ProvUIActivator.PLUGIN_ID, "ACCUMULATE_LOAD_ERRORS"); //$NON-NLS-1$

	private List repoCache = new ArrayList();
	private RepositoryTracker tracker;
	private MultiStatus accumulatedStatus;
	private URI[] locations;
	private ProvisioningUI ui;

	/**
	 * Create a job that loads the metadata repositories known by the specified RepositoryTracker.
	 * @param ui the ProvisioningUI providing the necessary services
	 */
	public LoadMetadataRepositoryJob(ProvisioningUI ui) {
		super(ProvUIMessages.LoadMetadataRepositoryJob_ContactSitesProgress, ui.getSession());
		this.ui = ui;
		this.tracker = ui.getRepositoryTracker();
		this.locations = tracker.getKnownRepositories(ui.getSession());
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.equinox.p2.operations.ProvisioningJob#runModal(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public IStatus runModal(IProgressMonitor monitor) {
		if (locations == null || locations.length == 0)
			return Status.OK_STATUS;

		// We batch all the time as a way of distinguishing client-initiated repository 
		// jobs from low level repository manipulation.
		ui.signalRepositoryOperationStart();
		try {
			doLoad(monitor);
		} finally {
			ui.signalRepositoryOperationComplete(null, getProperty(SUPPRESS_REPOSITORY_EVENTS) == null);
		}
		return Status.OK_STATUS;
	}

	private IStatus doLoad(IProgressMonitor monitor) {
		ui.signalRepositoryOperationStart();
		try {
			SubMonitor sub = SubMonitor.convert(monitor, ProvUIMessages.LoadMetadataRepositoryJob_ContactSitesProgress, locations.length * 100);
			if (sub.isCanceled())
				return Status.CANCEL_STATUS;
			for (int i = 0; i < locations.length; i++) {
				if (sub.isCanceled())
					return Status.CANCEL_STATUS;
				try {
					repoCache.add(ui.getSession().getMetadataRepositoryManager().loadRepository(locations[i], sub.newChild(100)));
				} catch (ProvisionException e) {
					handleLoadFailure(e, locations[i]);
				}
			}
		} finally {
			// we only want to update the UI if this was a user visible
			// job
			ui.signalRepositoryOperationComplete(null, isUser());
		}
		return getCurrentStatus();
	}

	private void handleLoadFailure(ProvisionException e, URI location) {
		if (shouldAccumulateFailures()) {
			// Some ProvisionExceptions include an empty multi status with a message.  
			// Since empty multi statuses have a severity OK, The platform status handler doesn't handle
			// this well.  We correct this by recreating a status with error severity
			// so that the platform status handler does the right thing.
			IStatus status = e.getStatus();
			if (status instanceof MultiStatus && ((MultiStatus) status).getChildren().length == 0)
				status = new Status(IStatus.ERROR, status.getPlugin(), status.getCode(), status.getMessage(), status.getException());
			if (accumulatedStatus == null) {
				accumulatedStatus = new MultiStatus(ProvUIActivator.PLUGIN_ID, ProvisionException.REPOSITORY_NOT_FOUND, new IStatus[] {status}, ProvUIMessages.LoadMetadataRepositoryJob_SitesMissingError, null);
			} else {
				accumulatedStatus.add(status);
			}
			ui.getRepositoryTracker().addNotFound(location);
			// Always log the complete exception so the detailed stack trace is in the log.  
			LogHelper.log(e);
		} else {
			tracker.reportLoadFailure(location, e);
		}
	}

	private boolean shouldAccumulateFailures() {
		return getProperty(LoadMetadataRepositoryJob.SUPPRESS_AUTHENTICATION_JOB_MARKER) != null;
	}

	/**
	 * Report the accumulated status to the repository tracker.  If there has been
	 * no status accumulated, or if the job has been cancelled, do not report
	 * anything.
	 */
	public void reportAccumulatedStatus() {
		IStatus status = getCurrentStatus();
		if (status.isOK() || status.getSeverity() == IStatus.CANCEL)
			return;
		// report status
		int flags = StatusManager.LOG;
		if (ui.getPolicy().getRepositoriesVisible())
			flags = flags | StatusManager.SHOW;
		StatusManager.getManager().handle(status, flags);
		// Reset the accumulated status so that next time we only report the newly not found repos.
		accumulatedStatus = null;
	}

	private IStatus getCurrentStatus() {
		if (accumulatedStatus != null) {
			// If there is only missing repo to report, use the specific message rather than the generic.
			if (accumulatedStatus.getChildren().length == 1)
				return accumulatedStatus.getChildren()[0];
			return accumulatedStatus;
		}
		return Status.OK_STATUS;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.core.runtime.jobs.Job#belongsTo(java.lang.Object)
	 */
	public boolean belongsTo(Object family) {
		return family == LOAD_FAMILY;
	}
}
