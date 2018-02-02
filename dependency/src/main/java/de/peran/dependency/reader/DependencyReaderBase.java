package de.peran.dependency.reader;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import de.peran.dependency.ChangeManager;
import de.peran.dependency.DependencyManager;
import de.peran.dependency.analysis.data.CalledMethods;
import de.peran.dependency.analysis.data.ChangeTestMapping;
import de.peran.dependency.analysis.data.TestDependencies;
import de.peran.dependency.analysis.data.TestExistenceChanges;
import de.peran.dependency.analysis.data.TestSet;
import de.peran.generated.Versiondependencies;
import de.peran.generated.Versiondependencies.Initialversion;
import de.peran.generated.Versiondependencies.Initialversion.Initialdependency;
import de.peran.generated.Versiondependencies.Versions.Version;
import de.peran.generated.Versiondependencies.Versions.Version.Dependency;
import de.peran.generated.Versiondependencies.Versions.Version.Dependency.Testcase;
import de.peran.vcs.VersionIterator;

/**
 * Shared functions for dependency reading, which are both used if dependencies are read fully or if one continues a dependency reading process.
 * 
 * @author reichelt
 *
 */
public abstract class DependencyReaderBase {

	private static final boolean DETAIL_DEBUG = true;
	private static final File DEBUG_FOLDER = new File("debug");
	final static ObjectMapper OBJECTMAPPER = new ObjectMapper();

	static {
		if (DETAIL_DEBUG) {
			if (!DEBUG_FOLDER.exists()) {
				DEBUG_FOLDER.mkdir();
			}
		}
		OBJECTMAPPER.enable(SerializationFeature.INDENT_OUTPUT);
	}

	private static final Logger LOG = LogManager.getLogger(DependencyReaderBase.class);

	protected final Versiondependencies dependencyResult;
	protected final File dependencyFile;
	protected DependencyManager handler;
	protected final File projectFolder;
	protected VersionIterator iterator;
	protected TestDependencies dependencyMap;

	/**
	 * Initializes the reader with the given result-object, the folder to examine an the file to write to
	 * @param dependencyResult	Object to write results to
	 * @param projectFolder	Folder to examine
	 * @param dependencyFile	File to write results to
	 */
	public DependencyReaderBase(final Versiondependencies dependencyResult, final File projectFolder, final File dependencyFile) {
		super();
		this.dependencyResult = dependencyResult;
		this.dependencyFile = dependencyFile;
		this.projectFolder = projectFolder;

	}

	protected static Initialversion createInitialVersion(final String startVersion, final TestDependencies dependencyMap, final int jdkversion) {
		final Initialversion initialversion = new Initialversion();
		initialversion.setVersion(startVersion);
		initialversion.setJdk(new BigInteger("" + jdkversion));
		LOG.debug("Starting writing: {}", dependencyMap.getDependencyMap().size());
		for (final Entry<String, CalledMethods> dependencyEntry : dependencyMap.getDependencyMap().entrySet()) {
			final Initialdependency dependency = new Initialdependency();
			dependency.setTestclass(dependencyEntry.getKey());
			for (final Map.Entry<String, Set<String>> dependentClassEntry : dependencyEntry.getValue().getCalledMethods().entrySet()) {
				final String dependentclass = dependentClassEntry.getKey();
				if (!dependentclass.contains("junit") && !dependentclass.contains("log4j")) {
					for (final String dependentmethod : dependentClassEntry.getValue()) {
						dependency.getDependentclass().add(dependentclass + "." + dependentmethod);
					}
				}

			}
			initialversion.getInitialdependency().add(dependency);
		}
		return initialversion;
	}

	protected DependencyManager readCompletedVersions() {
		// final DependencyManager handler = ;
		for (final Initialdependency dependency : dependencyResult.getInitialversion().getInitialdependency()) {
			for (final String dependentClass : dependency.getDependentclass()) {
				final Map<String, Set<String>> dependents = handler.getDependencyMap().getDependenciesForTest(dependency.getTestclass());
				final String clazz = dependentClass.substring(0, dependentClass.lastIndexOf("."));
				final String method = dependentClass.substring(dependentClass.lastIndexOf(".") + 1);
				Set<String> methods = dependents.get(clazz);
				if (methods == null) {
					methods = new HashSet<>();
					dependents.put(clazz, methods);
				}
				methods.add(method);
			}
		}
		dependencyMap = handler.getDependencyMap();
		final Initialversion initialversion = createInitialVersion(iterator.getTag(), dependencyMap, dependencyResult.getInitialversion().getJdk().intValue());
		dependencyResult.setInitialversion(initialversion);
		DependencyReaderUtil.write(dependencyResult, dependencyFile);
		// writeInitialDependency(, dependencyMap, );

		if (dependencyResult.getVersions().getVersion().size() > 0) {
			for (final Version version : dependencyResult.getVersions().getVersion()) {
				for (final Dependency dependency : version.getDependency()) {
					for (final Testcase testcase : dependency.getTestcase()) {
						for (final String testMethod : testcase.getMethod()) {
							final String changedClazzName = dependency.getChangedclass().substring(0, dependency.getChangedclass().lastIndexOf("."));
							final String changedMethodName = dependency.getChangedclass().substring(dependency.getChangedclass().lastIndexOf(".") + 1);
							final Map<String, Set<String>> calledClasses = new HashMap<>();
							final Set<String> methods = new HashSet<>();
							methods.add(changedMethodName);
							calledClasses.put(changedClazzName, methods);
							handler.addDependencies(testcase.getClazz(), testMethod, calledClasses);
						}
					}
				}
			}
			DependencyReaderUtil.write(dependencyResult, dependencyFile);
		}

		LOG.debug("Analysiere {} Einträge", iterator.getSize());
		return handler;
	}

	/**
	 * Determines the tests that may have got new dependencies, writes that changes (i.e. the tests that need to be run in that version) and re-runs the tests in order to get the updated test
	 * dependencies.
	 * 
	 * @param dependencyFile
	 * @param handler
	 * @param dependencies
	 * @param dependencyResult
	 * @param version
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public int analyseVersion(final ChangeManager changeManager)
			throws IOException {
		if (!handler.getExecutor().isVersionRunning()) {
			LOG.error("Version not running");
			return 0;
		}

		final Map<String, Set<String>> changes = changeManager.getChanges();
		changeManager.saveOldClasses();

		if (changes.size() > 0) {
			final ChangeTestMapping changeTestMap = DependencyReaderUtil.getChangeTestMap(handler.getDependencyMap(), changes); // tells which tests need to be run, and because of which change they
																																// need
			// to be run

			final String tag = iterator.getTag();
			if (DETAIL_DEBUG)
				OBJECTMAPPER.writeValue(new File(DEBUG_FOLDER, "changetest_" + tag + ".json"), changeTestMap);

			final Version newVersionInfo = DependencyReaderUtil.createVersionFromChangeMap(tag, changes, changeTestMap);
			newVersionInfo.setJdk(new BigInteger("" + handler.getExecutor().getJDKVersion()));

			if (DETAIL_DEBUG)
				OBJECTMAPPER.writeValue(new File(DEBUG_FOLDER, "versioninfo_" + tag + ".json"), newVersionInfo.getDependency());

			LOG.debug("Updating dependencies.. {}", tag);

			final TestSet testsToRun = handler.getTestsToRun(changes); // contains only the tests that need to be run -> could be changeTestMap.values() und dann umwandeln
			OBJECTMAPPER.writeValue(new File(DEBUG_FOLDER, "toRun_" + tag + ".json"), testsToRun.entrySet());
			if (testsToRun.size() > 0) {
				final TestExistenceChanges testExistenceChanges = handler.updateDependencies(testsToRun, tag);
				final Map<String, Set<String>> newTestcases = testExistenceChanges.getAddedTests();

				if (DETAIL_DEBUG) {
					OBJECTMAPPER.writeValue(new File(DEBUG_FOLDER, "add_" + tag + ".json"), newTestcases);
					OBJECTMAPPER.writeValue(new File(DEBUG_FOLDER, "remove_" + tag + ".json"), testExistenceChanges.getRemovedTests());
				}

				DependencyReaderUtil.removeDeletedTestcases(newVersionInfo, testExistenceChanges);
				DependencyReaderUtil.addNewTestcases(newVersionInfo, newTestcases);

				if (DETAIL_DEBUG)
					OBJECTMAPPER.writeValue(new File(DEBUG_FOLDER, "final_" + newVersionInfo.getVersion() + ".json"), newVersionInfo);

				dependencyResult.getVersions().getVersion().add(newVersionInfo);

				return newVersionInfo.getDependency().stream().mapToInt(dependency -> dependency.getTestcase().size()).sum();
			} else {
				return testsToRun.size();
			}
		} else {
			return 0;
		}

	}
	
	public boolean readInitialVersion() throws IOException, InterruptedException {
		if (!handler.initialyGetTraces()) {
			return false;
		}
		dependencyMap = handler.getDependencyMap();
		final Initialversion initialversion = createInitialVersion(iterator.getTag(), dependencyMap, handler.getExecutor().getJDKVersion());
		dependencyResult.setInitialversion(initialversion);
		DependencyReaderUtil.write(dependencyResult, dependencyFile);

		return true;
	}
}