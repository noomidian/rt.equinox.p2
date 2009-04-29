package org.eclipse.equinox.p2.tests.ant;

import java.io.File;
import java.net.URI;
import java.util.Collection;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.internal.provisional.p2.artifact.repository.IArtifactDescriptor;
import org.eclipse.equinox.internal.provisional.p2.artifact.repository.IArtifactRepository;
import org.eclipse.equinox.internal.provisional.p2.core.ProvisionException;
import org.eclipse.equinox.internal.provisional.p2.metadata.IArtifactKey;
import org.eclipse.equinox.internal.provisional.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.InstallableUnitQuery;
import org.eclipse.equinox.internal.provisional.p2.metadata.repository.IMetadataRepository;
import org.eclipse.equinox.internal.provisional.p2.query.Collector;
import org.eclipse.equinox.p2.tests.AbstractAntProvisioningTest;

public class Repo2RunnableTaskTests extends AbstractAntProvisioningTest {

	private URI destination, source;

	public void setUp() throws Exception {
		source = getTestData("Error loading data", "testData/mirror/mirrorSourceRepo1 with space").toURI();
		destination = getTempFolder().toURI();
		super.setUp();
	}

	public void tearDown() throws Exception {
		getArtifactRepositoryManager().removeRepository(source);
		getMetadataRepositoryManager().removeRepository(source);
		getArtifactRepositoryManager().removeRepository(destination);
		getMetadataRepositoryManager().removeRepository(destination);
		delete(new File(destination));
		super.tearDown();
	}

	/*
	 * Test the Repo2RunnableTask functions as expected on a simple repository
	 */
	public void testRepo2Runnable() {
		createRepo2RunnableTaskElement(TYPE_BOTH);

		runAntTask();
		assertEquals("Number of artifact keys differs", getArtifactKeyCount(source), getArtifactKeyCount(destination));
		assertTrue("Unexpected format", expectedFormat(destination));
	}

	/*
	 * Test that when an IU is specified that it is used
	 */
	public void testRepo2RunnableSpecifiedIU() {
		IInstallableUnit iu = null;
		try {
			IMetadataRepository repo = getMetadataRepositoryManager().loadRepository(source, new NullProgressMonitor());
			Collection ius = repo.query(new InstallableUnitQuery("helloworldfeature.feature.jar"), new Collector(), new NullProgressMonitor()).toCollection();
			assertEquals("Expected number of IUs", 1, ius.size());
			iu = (IInstallableUnit) ius.iterator().next();
		} catch (ProvisionException e) {
			fail("Failed to obtain iu", e);
		}
		AntTaskElement task = createRepo2RunnableTaskElement(TYPE_BOTH);
		task.addElement(createIUElement(iu));

		runAntTask();
		assertEquals("Number of artifact keys differs", iu.getArtifacts().length, getArtifactKeyCount(destination));
		assertTrue("Unexpected format", expectedFormat(destination));
	}

	/*
	 * Ensure that the output repository is of the expected type
	 */
	protected boolean expectedFormat(URI location) {
		IArtifactRepository repo = null;
		try {
			repo = getArtifactRepositoryManager().loadRepository(location, new NullProgressMonitor());
		} catch (ProvisionException e) {
			fail("Failed to load repository", e);
		}
		IArtifactKey[] keys = repo.getArtifactKeys();
		for (int i = 0; i < keys.length; i++) {
			IArtifactKey key = keys[i];
			IArtifactDescriptor[] descriptors = repo.getArtifactDescriptors(key);
			for (int n = 0; n < descriptors.length; n++) {
				IArtifactDescriptor desc = descriptors[n];
				// Features should be unzipped, others should not be.
				boolean isFolder = desc.getProperty("artifact.folder") != null ? Boolean.valueOf(desc.getProperty("artifact.folder")) : false;
				if (key.getClassifier().equals(""))
					assertTrue(desc + " is not a folder", isFolder);
				else
					assertFalse(desc + " is a folder", isFolder);
				// Artifacts should not be packed
				assertTrue("Artifact is still packed", !"packed".equals(desc.getProperty("format")));
			}
		}
		return true;
	}

	/*
	 * Count the number of ArtifactKeys in the repository at the given location 
	 */
	protected int getArtifactKeyCount(URI location) {
		try {
			return getArtifactRepositoryManager().loadRepository(location, new NullProgressMonitor()).getArtifactKeys().length;
		} catch (ProvisionException e) {
			fail("Failed to count keys in repository", e);
			return -1;
		}
	}

	/*
	 * Create a simple AntTaskElement for the Repo2RunnableTask
	 */
	protected AntTaskElement createRepo2RunnableTaskElement() {
		AntTaskElement task = new AntTaskElement("p2.repo2runnable");
		addTask(task);
		return task;
	}

	/*
	 * Create an AntTaskElement for the Repo2Runnabletask populated with the default source & destination 
	 */
	protected AntTaskElement createRepo2RunnableTaskElement(String type) {
		AntTaskElement task = createRepo2RunnableTaskElement();
		task.addElement(getRepositoryElement(destination, type));

		AntTaskElement sourceElement = new AntTaskElement("source");
		sourceElement.addElement(getRepositoryElement(source, type));
		task.addElement(sourceElement);

		return task;
	}
}