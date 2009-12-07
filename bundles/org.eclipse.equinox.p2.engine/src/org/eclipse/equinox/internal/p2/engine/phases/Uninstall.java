/*******************************************************************************
 *  Copyright (c) 2007, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.engine.phases;

import org.eclipse.equinox.p2.metadata.IInstallableUnit;

import org.eclipse.equinox.p2.metadata.IArtifactKey;

import java.util.Map;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.internal.p2.engine.Profile;
import org.eclipse.equinox.internal.provisional.p2.core.eventbus.IProvisioningEventBus;
import org.eclipse.equinox.internal.provisional.p2.engine.*;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.engine.IPhaseSet;
import org.eclipse.equinox.p2.engine.spi.ProvisioningAction;
import org.eclipse.equinox.p2.engine.spi.Touchpoint;

public class Uninstall extends InstallableUnitPhase {

	final static class BeforeUninstallEventAction extends ProvisioningAction {
		public IStatus execute(Map parameters) {
			IProfile profile = (IProfile) parameters.get(PARM_PROFILE);
			String phaseId = (String) parameters.get(PARM_PHASE_ID);
			InstallableUnitOperand operand = (InstallableUnitOperand) parameters.get(PARM_OPERAND);
			IProvisioningAgent agent = (IProvisioningAgent) parameters.get(PARM_AGENT);
			((IProvisioningEventBus) agent.getService(IProvisioningEventBus.SERVICE_NAME)).publishEvent(new InstallableUnitEvent(phaseId, true, profile, operand, InstallableUnitEvent.UNINSTALL, getTouchpoint()));
			return null;
		}

		public IStatus undo(Map parameters) {
			Profile profile = (Profile) parameters.get(PARM_PROFILE);
			String phaseId = (String) parameters.get(PARM_PHASE_ID);
			InstallableUnitOperand operand = (InstallableUnitOperand) parameters.get(PARM_OPERAND);
			IInstallableUnit iu = (IInstallableUnit) parameters.get(PARM_IU);
			profile.addInstallableUnit(iu);
			IProvisioningAgent agent = (IProvisioningAgent) parameters.get(PARM_AGENT);
			((IProvisioningEventBus) agent.getService(IProvisioningEventBus.SERVICE_NAME)).publishEvent(new InstallableUnitEvent(phaseId, false, profile, operand, InstallableUnitEvent.INSTALL, getTouchpoint()));
			return null;
		}
	}

	final static class AfterUninstallEventAction extends ProvisioningAction {
		public IStatus execute(Map parameters) {
			Profile profile = (Profile) parameters.get(PARM_PROFILE);
			String phaseId = (String) parameters.get(PARM_PHASE_ID);
			InstallableUnitOperand operand = (InstallableUnitOperand) parameters.get(PARM_OPERAND);
			IInstallableUnit iu = (IInstallableUnit) parameters.get(PARM_IU);
			profile.removeInstallableUnit(iu);
			IProvisioningAgent agent = (IProvisioningAgent) parameters.get(PARM_AGENT);
			((IProvisioningEventBus) agent.getService(IProvisioningEventBus.SERVICE_NAME)).publishEvent(new InstallableUnitEvent(phaseId, false, profile, operand, InstallableUnitEvent.UNINSTALL, getTouchpoint()));
			return null;
		}

		public IStatus undo(Map parameters) {
			IProfile profile = (IProfile) parameters.get(PARM_PROFILE);
			String phaseId = (String) parameters.get(PARM_PHASE_ID);
			InstallableUnitOperand operand = (InstallableUnitOperand) parameters.get(PARM_OPERAND);
			IProvisioningAgent agent = (IProvisioningAgent) parameters.get(PARM_AGENT);
			((IProvisioningEventBus) agent.getService(IProvisioningEventBus.SERVICE_NAME)).publishEvent(new InstallableUnitEvent(phaseId, true, profile, operand, InstallableUnitEvent.INSTALL, getTouchpoint()));
			return null;
		}
	}

	public Uninstall(int weight, boolean forced) {
		super(IPhaseSet.PHASE_UNINSTALL, weight, forced);
	}

	public Uninstall(int weight) {
		this(weight, false);
	}

	protected boolean isApplicable(InstallableUnitOperand op) {
		return (op.first() != null && !op.first().equals(op.second()));
	}

	protected ProvisioningAction[] getActions(InstallableUnitOperand currentOperand) {
		//TODO: monitor.subTask(NLS.bind(Messages.Engine_Uninstalling_IU, unit.getId()));

		ProvisioningAction beforeAction = new BeforeUninstallEventAction();
		ProvisioningAction afterAction = new AfterUninstallEventAction();

		IInstallableUnit unit = currentOperand.first();
		Touchpoint touchpoint = getActionManager().getTouchpointPoint(unit.getTouchpointType());
		if (touchpoint != null) {
			beforeAction.setTouchpoint(touchpoint);
			afterAction.setTouchpoint(touchpoint);
		}

		if (unit.isFragment())
			return new ProvisioningAction[] {beforeAction, afterAction};
		ProvisioningAction[] parsedActions = getActions(unit, phaseId);
		if (parsedActions == null)
			return new ProvisioningAction[] {beforeAction, afterAction};

		ProvisioningAction[] actions = new ProvisioningAction[parsedActions.length + 2];
		actions[0] = beforeAction;
		System.arraycopy(parsedActions, 0, actions, 1, parsedActions.length);
		actions[actions.length - 1] = afterAction;
		return actions;
	}

	protected String getProblemMessage() {
		return Messages.Phase_Uninstall_Error;
	}

	protected IStatus initializeOperand(IProfile profile, InstallableUnitOperand operand, Map parameters, IProgressMonitor monitor) {
		IInstallableUnit iu = operand.first();
		parameters.put(PARM_IU, iu);

		IArtifactKey[] artifacts = iu.getArtifacts();
		if (artifacts != null && artifacts.length > 0)
			parameters.put(PARM_ARTIFACT, artifacts[0]);

		return Status.OK_STATUS;
	}
}