package com.compuware.touchpoint;

import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.p2.engine.spi.ProvisioningAction;

public class TouchpointAction extends ProvisioningAction {
	
	@Override
	public IStatus execute(Map<String, Object> parameters) {
		System.out.println("The touchpoint got executed !");
		return Status.OK_STATUS;
	}
	
	@Override
	public IStatus undo(Map<String, Object> parameters) {
		/* NOOP */
		return Status.OK_STATUS;
	}
}