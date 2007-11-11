/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.equinox.p2.engine;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import javax.xml.parsers.ParserConfigurationException;
import org.eclipse.equinox.internal.p2.core.helpers.OrderedProperties;
import org.eclipse.equinox.internal.p2.core.helpers.ServiceHelper;
import org.eclipse.equinox.internal.p2.engine.*;
import org.eclipse.equinox.internal.p2.persistence.XMLWriter;
import org.eclipse.equinox.p2.core.eventbus.ProvisioningEventBus;
import org.eclipse.equinox.p2.core.location.AgentLocation;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.osgi.service.resolver.VersionRange;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;
import org.xml.sax.*;

public class SimpleProfileRegistry implements IProfileRegistry {
	private static String STORAGE = "profileRegistry.xml"; //$NON-NLS-1$

	/**
	 * Map of String(Profile id)->Profile. 
	 */
	LinkedHashMap profiles = new LinkedHashMap(8);

	OrderedProperties properties = new OrderedProperties();

	private String self;

	public SimpleProfileRegistry() {
		self = EngineActivator.getContext().getProperty("eclipse.p2.profile"); //$NON-NLS-1$
		restore();
		updateRoamingProfile();
	}

	/**
	 * If the current profile for self is marked as a roaming profile, we need
	 * to update its install and bundle pool locations.
	 */
	private void updateRoamingProfile() {
		Profile selfProfile = getProfile(SELF);
		if (selfProfile == null)
			return;
		//only update if self is a roaming profile
		if (!Boolean.valueOf(selfProfile.getValue(Profile.PROP_ROAMING)).booleanValue())
			return;
		Location installLocation = (Location) ServiceHelper.getService(EngineActivator.getContext(), Location.class.getName(), Location.INSTALL_FILTER);
		String locationString = installLocation.getURL().getPath();
		boolean changed = false;
		if (!locationString.equals(selfProfile.getValue(Profile.PROP_INSTALL_FOLDER))) {
			selfProfile.setValue(Profile.PROP_INSTALL_FOLDER, locationString);
			changed = true;
		}
		if (!locationString.equals(selfProfile.getValue(Profile.PROP_CACHE))) {
			selfProfile.setValue(Profile.PROP_CACHE, locationString);
			changed = true;
		}
		if (changed)
			persist();
	}

	public String toString() {
		return this.profiles.toString();
	}

	public Profile getProfile(String id) {
		if (SELF.equals(id))
			id = self;
		return (Profile) profiles.get(id);
	}

	public Profile[] getProfiles() {
		return (Profile[]) profiles.values().toArray(new Profile[profiles.size()]);
	}

	public void addProfile(Profile toAdd) throws IllegalArgumentException {
		if (isNamedSelf(toAdd))
			throw new IllegalArgumentException(NLS.bind(Messages.Profile_Not_Named_Self, toAdd.getProfileId()));
		String id = toAdd.getProfileId();
		if (getProfile(id) == null) {
			profiles.put(id, toAdd);
		} else
			throw new IllegalArgumentException(NLS.bind(Messages.Profile_Duplicate_Root_Profile_Id, id));
		broadcastChangeEvent(toAdd, ProfileEvent.ADDED);
		persist(); //TODO This is not enough to keep track of the changes that are being done in a profile. This will likely have to be based on some event like commit
	}

	private void broadcastChangeEvent(Profile profile, byte reason) {
		((ProvisioningEventBus) ServiceHelper.getService(EngineActivator.getContext(), ProvisioningEventBus.class.getName())).publishEvent(new ProfileEvent(profile, reason));
	}

	private URL getRegistryLocation() {
		AgentLocation agent = (AgentLocation) ServiceHelper.getService(EngineActivator.getContext(), AgentLocation.class.getName());
		try {
			return new URL(agent.getDataArea(EngineActivator.ID), STORAGE);
		} catch (MalformedURLException e) {
			//this is not possible because we know the above URL is valid
		}
		return null;
	}

	private void restore() {
		try {
			BufferedInputStream bif = null;
			try {
				bif = new BufferedInputStream(getRegistryLocation().openStream());
				Parser parser = new Parser(EngineActivator.getContext(), EngineActivator.ID);
				parser.parse(bif);
			} finally {
				if (bif != null)
					bif.close();
			}
		} catch (FileNotFoundException e) {
			//This is ok.
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void persist() {
		OutputStream os;
		try {
			Location agent = (Location) ServiceHelper.getService(EngineActivator.getContext(), AgentLocation.class.getName());
			if (agent == null)
				// TODO should likely do something here since we failed to persist.
				return;

			URL registryLocation = getRegistryLocation();
			if (!registryLocation.getProtocol().equals("file")) //$NON-NLS-1$
				throw new IOException(NLS.bind(Messages.SimpleProfileRegistry_Persist_To_Non_File_URL_Error, registryLocation));

			File outputFile = new File(registryLocation.toExternalForm().substring(5));
			if (!outputFile.getParentFile().exists() && !outputFile.getParentFile().mkdirs())
				throw new RuntimeException(NLS.bind(Messages.SimpleProfileRegistry_Cannot_Create_File_Error, outputFile));
			os = new BufferedOutputStream(new FileOutputStream(outputFile));
			try {
				Writer writer = new Writer(os);
				writer.write(this);
			} finally {
				os.close();
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void removeProfile(Profile toRemove) {
		if (isNamedSelf(toRemove))
			throw new IllegalArgumentException(NLS.bind(Messages.Profile_Not_Named_Self, toRemove.getProfileId()));
		if (profiles.remove(toRemove.getProfileId()) == null)
			return;
		broadcastChangeEvent(toRemove, ProfileEvent.REMOVED);
		persist();
	}

	private boolean isNamedSelf(Profile p) {
		if (SELF.equals(p.getParentProfile()))
			return true;
		return false;
	}

	public Map getProperties() {
		return properties;
	}

	public String getProperty(String key) {
		return properties.getProperty(key);
	}

	public void setProperty(String key, String value) {
		properties.setProperty(key, value);
	}

	public void removeProperty(String key) {
		properties.remove(key);
	}

	private SimpleProfileRegistry getRegistry() {
		return this;
	}

	private interface XMLConstants extends org.eclipse.equinox.internal.p2.persistence.XMLConstants {

		// Constants defining the structure of the XML for a SimpleProfileRegistry

		// A format version number for simple profile registry XML.
		public static final String XML_CURRENT = "0.0.2"; //$NON-NLS-1$
		public static final Version CURRENT_VERSION = new Version(XML_CURRENT);
		public static final String XML_COMPATIBLE = "0.0.1"; //$NON-NLS-1$
		public static final Version COMPATIBLE_VERSION = new Version(XML_CURRENT);
		public static final VersionRange XML_TOLERANCE = new VersionRange(COMPATIBLE_VERSION, true, CURRENT_VERSION, true);

		// Constants for processing instructions
		public static final String PI_REPOSITORY_TARGET = "profileRegistry"; //$NON-NLS-1$
		public static XMLWriter.ProcessingInstruction[] PI_DEFAULTS = new XMLWriter.ProcessingInstruction[] {XMLWriter.ProcessingInstruction.makeClassVersionInstruction(PI_REPOSITORY_TARGET, SimpleProfileRegistry.class, CURRENT_VERSION)};

		// Constants for profile registry elements
		public static final String REGISTRY_ELEMENT = "profileRegistry"; //$NON-NLS-1$

	}

	protected class Writer extends ProfileWriter implements XMLConstants {

		public Writer(OutputStream output) throws IOException {
			super(output, PI_DEFAULTS);
		}

		/**
		 * Write the given SimpleProfileRegistry to the output stream.
		 */
		public void write(SimpleProfileRegistry registry) {
			start(REGISTRY_ELEMENT);
			writeProperties(registry.getProperties());
			writeProfiles(registry.getProfiles());
			end(REGISTRY_ELEMENT);
			flush();
		}
	}

	/*
	 * 	Parser for the contents of a SimpleProfileRegistry,
	 * 	as written by the Writer class.
	 */
	private class Parser extends ProfileParser implements XMLConstants {

		public Parser(BundleContext context, String bundleId) {
			super(context, bundleId);
		}

		public void parse(File file) throws IOException {
			parse(new FileInputStream(file));
		}

		public synchronized void parse(InputStream stream) throws IOException {
			this.status = null;
			try {
				// TODO: currently not caching the parser since we make no assumptions
				//		 or restrictions on concurrent parsing
				getParser();
				RegistryHandler registryHandler = new RegistryHandler();
				xmlReader.setContentHandler(new ProfileRegistryDocHandler(REGISTRY_ELEMENT, registryHandler));
				xmlReader.parse(new InputSource(stream));
			} catch (SAXException e) {
				throw new IOException(e.getMessage());
			} catch (ParserConfigurationException e) {
				throw new IOException(e.getMessage());
			} finally {
				stream.close();
			}
		}

		public SimpleProfileRegistry getContainingRegistry() {
			return getRegistry();
		}

		protected Object getRootObject() {
			return getRegistry();
		}

		private final class ProfileRegistryDocHandler extends DocHandler {

			public ProfileRegistryDocHandler(String rootName, RootHandler rootHandler) {
				super(rootName, rootHandler);
			}

			public void ProcessingInstruction(String target, String data) throws SAXException {
				if (PI_REPOSITORY_TARGET.equals(target)) {
					// TODO: should the root handler be constructed based on class
					// 		 via an extension registry mechanism?
					// String clazz = extractPIClass(data);
					// and
					// TODO: version tolerance by extension
					Version repositoryVersion = extractPIVersion(target, data);
					if (!XMLConstants.XML_TOLERANCE.isIncluded(repositoryVersion)) {
						throw new SAXException(NLS.bind(Messages.SimpleProfileRegistry_Parser_Has_Incompatible_Version, repositoryVersion, XMLConstants.XML_TOLERANCE));
					}
				}
			}

		}

		private final class RegistryHandler extends RootHandler {

			private ProfilesHandler profilesHandler = null;
			private PropertiesHandler propertiesHandler = null;

			public RegistryHandler() {
				super();
			}

			protected void handleRootAttributes(Attributes attributes) {
				parseRequiredAttributes(attributes, noAttributes);
			}

			public void startElement(String name, Attributes attributes) {
				if (PROPERTIES_ELEMENT.equals(name)) {
					if (propertiesHandler == null) {
						propertiesHandler = new PropertiesHandler(this, attributes);
					} else {
						duplicateElement(this, name, attributes);
					}
				} else if (PROFILES_ELEMENT.equals(name)) {
					if (profilesHandler == null) {
						profilesHandler = new ProfilesHandler(this, attributes, null /*no parent*/);
					} else {
						duplicateElement(this, name, attributes);
					}
				} else {
					invalidElement(name, attributes);
				}
			}

			protected void finished() {
				if (isValidXML()) {
					Profile[] profyles = (profilesHandler == null ? new Profile[0] //
							: profilesHandler.getProfiles());
					for (int i = 0; i < profyles.length; i++) {
						Profile nextProfile = profyles[i];
						profiles.put(nextProfile.getProfileId(), nextProfile);
					}
					properties = (propertiesHandler == null ? new OrderedProperties(0) //
							: propertiesHandler.getProperties());
				}
			}
		}

		protected class ProfilesHandler extends AbstractHandler {

			private Profile parentProfile = null;
			private List profyles = null;

			public ProfilesHandler(AbstractHandler parentHandler, Attributes attributes, Profile parent) {
				super(parentHandler, PROFILES_ELEMENT);
				this.parentProfile = parent;
				String size = parseOptionalAttribute(attributes, COLLECTION_SIZE_ATTRIBUTE);
				profyles = (size != null ? new ArrayList(new Integer(size).intValue()) : new ArrayList(4));
			}

			public Profile[] getProfiles() {
				return (Profile[]) profyles.toArray(new Profile[profyles.size()]);
			}

			public void startElement(String name, Attributes attributes) {
				if (name.equals(PROFILE_ELEMENT)) {
					new ProfileHandler(this, attributes, parentProfile, profyles);
				} else {
					invalidElement(name, attributes);
				}
			}
		}

		protected class ProfileHandler extends AbstractHandler {

			private final String[] required = new String[] {ID_ATTRIBUTE};

			List profyles = null;
			Profile currentProfile = null;

			private String profileId = null;

			private PropertiesHandler propertiesHandler = null;
			private ProfilesHandler profilesHandler = null;

			public ProfileHandler(AbstractHandler parentHandler, Attributes attributes, Profile parent, List profiles) {
				super(parentHandler, PROFILE_ELEMENT);
				profileId = parseRequiredAttributes(attributes, required)[0];
				this.profyles = profiles;
				currentProfile = new Profile((profileId != null ? profileId : "##invalid##"), parent); //$NON-NLS-1$
			}

			public void startElement(String name, Attributes attributes) {
				if (PROPERTIES_ELEMENT.equals(name)) {
					if (propertiesHandler == null) {
						propertiesHandler = new PropertiesHandler(this, attributes);
					} else {
						duplicateElement(this, name, attributes);
					}
				} else if (name.equals(PROFILES_ELEMENT)) {
					if (profilesHandler == null) {
						profilesHandler = new ProfilesHandler(this, attributes, currentProfile);
					} else {
						duplicateElement(this, name, attributes);
					}
				} else {
					invalidElement(name, attributes);
				}
			}

			protected void finished() {
				if (isValidXML() && currentProfile != null) {
					if (propertiesHandler != null) {
						currentProfile.addProperties(propertiesHandler.getProperties());
					}
				}
			}
		}

		protected String getErrorMessage() {
			return Messages.SimpleProfileRegistry_Parser_Error_Parsing_Registry;
		}

		public String toString() {
			// TODO:
			return null;
		}

	}
}
