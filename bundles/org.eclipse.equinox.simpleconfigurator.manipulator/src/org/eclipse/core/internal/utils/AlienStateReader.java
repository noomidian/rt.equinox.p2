package org.eclipse.core.internal.utils;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.configurationManipulator.BundleInfo;
import org.eclipse.core.runtime.adaptor.LocationManager;
import org.eclipse.osgi.framework.adaptor.FrameworkAdaptor;
import org.eclipse.osgi.framework.debug.Debug;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.PlatformAdmin;
import org.eclipse.osgi.service.resolver.State;
import org.eclipse.osgi.storagemanager.StorageManager;
import org.osgi.framework.BundleContext;

public class AlienStateReader {
	private StorageManager storageManager;
	private File configuration;
	private File parentConfiguration;
	private State state;

	public AlienStateReader(File configuration, File parentConfiguration) {
		this.configuration = configuration;
		this.parentConfiguration = parentConfiguration;
	}
	
	public BundleInfo[] getBundles() {
		PlatformAdmin platformAdmin = (PlatformAdmin) BundleHelper.getDefault().acquireService(PlatformAdmin.class.getName());
		if (platformAdmin == null)
			return null;

		try {
			File[] stateFiles = findStateFiles();
			if (stateFiles[0] == null || stateFiles[1] == null)
				return null;

			File tmpLocation = copyStateToTempLocation(stateFiles);
			try {
				state = platformAdmin.getFactory().readState(tmpLocation);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} finally {
			//in any case we need to close the storage manager
			if (storageManager != null)
				storageManager.close();
		}
		return convertState();
	}

	private BundleInfo[] convertState() {
		BundleDescription[] bundles = state.getBundles();
		BundleInfo[] result = new BundleInfo[bundles.length];
		for (int i = 0; i < bundles.length; i++) {
			result[i] = convert(bundles[i]);
		}
		return result;
	}
	
	private BundleInfo convert(BundleDescription toConvert) {
		BundleInfo result = new BundleInfo();
		result.setSymbolicName(toConvert.getSymbolicName());
		result.setVersion(toConvert.getVersion().toString());
		return result;
	}
	
	private File copyStateToTempLocation(File[] toCopy) {
		File temp = BundleHelper.getDefault().getDataFile("aliens");
		FileUtils.copy(toCopy[0], new File(temp, LocationManager.STATE_FILE));
		FileUtils.copy(toCopy[1], new File(temp, LocationManager.LAZY_FILE));
		return temp;
	}

	private File[] findStateFiles() {
		//TODO Need to do some cleanup with respect to constants coming from the fwk
		storageManager = initStorageManager(new File(configuration, FrameworkAdaptor.FRAMEWORK_SYMBOLICNAME), "none", false); //TODO Here we proabaly want to lock?
		File stateFile = null;
		File lazyFile = null;
		try {
			stateFile = storageManager.lookup(LocationManager.STATE_FILE, false);
			lazyFile = storageManager.lookup(LocationManager.LAZY_FILE, false);
		} catch (IOException ex) {
			//TODO I don't think there is much we can do in the this case.
			//We are only writing a manipulator
		}
		//if it does not exist, try to read it from the parent
		if (stateFile == null || !stateFile.isFile()) { // NOTE this check is redundant since it
			// is done in StateManager, however it
			// is more convenient to have it here
			if (parentConfiguration != null) {
				try {
					File stateLocationDir = new File(parentConfiguration, FrameworkAdaptor.FRAMEWORK_SYMBOLICNAME);
					storageManager = initStorageManager(stateLocationDir, "none", true); //$NON-NLS-1$);
					stateFile = storageManager.lookup(LocationManager.STATE_FILE, false);
					lazyFile = storageManager.lookup(LocationManager.LAZY_FILE, false);
				} catch (IOException ex) {
					if (Debug.DEBUG && Debug.DEBUG_GENERAL) {
						Debug.println("Error reading state file " + ex.getMessage()); //$NON-NLS-1$
						Debug.printStackTrace(ex);
					}
					stateFile = null;
					lazyFile = null;
				}
			}
		}
		return new File[] {stateFile, lazyFile};
	}

	private StorageManager initStorageManager(File baseDir, String lockMode, boolean readOnly) {
		StorageManager manager = new StorageManager(baseDir, lockMode, readOnly);
		try {
			manager.open(!readOnly);
		} catch (IOException ex) {
			//TODO Need to see what we want to do here
			//			if (Debug.DEBUG && Debug.DEBUG_GENERAL) {
			//				Debug.println("Error reading framework metadata: " + ex.getMessage()); //$NON-NLS-1$
			//				Debug.printStackTrace(ex);
			//			}
			//			String message = NLS.bind(EclipseAdaptorMsg.ECLIPSE_STARTUP_FILEMANAGER_OPEN_ERROR, ex.getMessage());
			//			FrameworkLogEntry logEntry = new FrameworkLogEntry(FrameworkAdaptor.FRAMEWORK_SYMBOLICNAME, FrameworkLogEntry.ERROR, 0, message, 0, ex, null);
			//			getFrameworkLog().log(logEntry);
		}
		return manager;
	}
}
