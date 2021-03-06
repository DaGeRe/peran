package de.dagere.peass.ci;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import javax.xml.bind.JAXBException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;

import de.dagere.peass.analysis.properties.PropertyReader;
import de.dagere.peass.config.DependencyConfig;
import de.dagere.peass.config.MeasurementConfiguration;
import de.dagere.peass.dependency.ExecutorCreator;
import de.dagere.peass.dependency.PeassFolders;
import de.dagere.peass.dependency.ResultsFolders;
import de.dagere.peass.dependency.analysis.ModuleClassMapping;
import de.dagere.peass.dependency.analysis.data.TestCase;
import de.dagere.peass.dependency.execution.EnvironmentVariables;
import de.dagere.peass.dependency.execution.TestExecutor;
import de.dagere.peass.dependency.persistence.ExecutionData;
import de.dagere.peass.measurement.analysis.AnalyseFullData;
import de.dagere.peass.measurement.analysis.ProjectStatistics;
import de.dagere.peass.testtransformation.TestTransformer;
import de.dagere.peass.utils.Constants;
import de.dagere.peass.vcs.GitUtils;
import de.dagere.peass.vcs.VersionControlSystem;
import de.dagere.peass.vcs.VersionIteratorGit;

public class ContinuousExecutor {

   private static final Logger LOG = LogManager.getLogger(ContinuousExecutor.class);

   private final MeasurementConfiguration measurementConfig;
   private final DependencyConfig dependencyConfig;

   private final String version;
   private String versionOld;
   private final VersionIteratorGit iterator;

   private final File originalProjectFolder;
   private final File localFolder;
   private final PeassFolders folders;
   private final ResultsFolders resultsFolders;

   private final EnvironmentVariables env;

   public ContinuousExecutor(final File projectFolder, final MeasurementConfiguration measurementConfig, final DependencyConfig dependencyConfig, final EnvironmentVariables env)
         throws InterruptedException, IOException {
      this.originalProjectFolder = projectFolder;
      this.measurementConfig = measurementConfig;
      this.dependencyConfig = dependencyConfig;
      this.env = env;
      LOG.info("Properties: " + env.getProperties());

      File vcsFolder = VersionControlSystem.findVCSFolder(projectFolder);
      localFolder = ContinuousFolderUtil.getLocalFolder(vcsFolder);
      File projectFolderLocal = new File(localFolder, ContinuousFolderUtil.getSubFolderPath(projectFolder));
      getGitRepo(projectFolder, measurementConfig, projectFolderLocal);
      resultsFolders = new ResultsFolders(localFolder, projectFolder.getName());

      folders = new PeassFolders(projectFolderLocal);

      IteratorBuilder iteratorBuiler = new IteratorBuilder(measurementConfig, folders.getProjectFolder());
      iterator = iteratorBuiler.getIterator();
      version = iteratorBuiler.getVersion();
      versionOld = iteratorBuiler.getVersionOld();
   }

   private void getGitRepo(final File projectFolder, final MeasurementConfiguration measurementConfig, final File projectFolderLocal) throws InterruptedException, IOException {
      if (!localFolder.exists() || !projectFolderLocal.exists()) {
         ContinuousFolderUtil.cloneProject(projectFolder, localFolder);
         if (!projectFolderLocal.exists()) {
            throw new RuntimeException("Was not able to clone project to " + projectFolderLocal.getAbsolutePath() + " (folder not existing)");
         }
      } else {
         GitUtils.reset(projectFolderLocal);
         GitUtils.clean(projectFolderLocal);
         GitUtils.pull(projectFolderLocal);
         GitUtils.goToTag(measurementConfig.getVersion(), projectFolderLocal);
      }
   }

   public Set<TestCase> executeRTS() {
      final String url = GitUtils.getURL(originalProjectFolder);

      Set<TestCase> tests = executeRegressionTestSelection(url);
      return tests;
   }
   
   public void measure(final Set<TestCase> tests) {
      try {
         File measurementFolder = executeMeasurement(tests);
         analyzeMeasurements(measurementFolder);
      } catch (IOException | InterruptedException | JAXBException | XmlPullParserException e) {
         throw new RuntimeException(e);
      }
   }

   public void execute() throws Exception {
      Set<TestCase> tests = executeRTS();
      measure(tests);
   }

   protected Set<TestCase> executeRegressionTestSelection(final String url) {
      ContinuousDependencyReader dependencyReader = new ContinuousDependencyReader(dependencyConfig, measurementConfig.getExecutionConfig(), folders, resultsFolders, env);
      final Set<TestCase> tests = dependencyReader.getTests(iterator, url, version, measurementConfig);
      versionOld = dependencyReader.getPredecessor();
      
      readMethodSources(tests);

      return tests;
   }

   private void readMethodSources(final Set<TestCase> tests) {
      ExecutionData executionData = new ExecutionData();
      executionData.addEmptyVersion(version, versionOld);
      executionData.addEmptyVersion(versionOld, null);
      for (TestCase test : tests) {
         executionData.addCall(version, test);
      }
      LOG.info("Reading method sources for {} - {}", version, versionOld);
      final PropertyReader propertyReader = new PropertyReader(resultsFolders, folders.getProjectFolder(), executionData);
      propertyReader.readAllTestsProperties();
   }

   protected File executeMeasurement(final Set<TestCase> tests) throws IOException, InterruptedException, JAXBException, XmlPullParserException {
      final File fullResultsVersion = getFullResultsVersion();
      final ContinuousMeasurementExecutor measurementExecutor = new ContinuousMeasurementExecutor(version, versionOld, folders, measurementConfig, env);
      final File measurementFolder = measurementExecutor.executeMeasurements(tests, fullResultsVersion);
      return measurementFolder;
   }

   private void analyzeMeasurements(final File measurementFolder) throws InterruptedException, IOException, JsonGenerationException, JsonMappingException, XmlPullParserException {
      final File changefile = new File(localFolder, "changes.json");
      // AnalyseOneTest.setResultFolder(new File(localFolder, version + "_graphs"));
      final ProjectStatistics statistics = new ProjectStatistics();
      TestTransformer testTransformer = ExecutorCreator.createTestTransformer(folders, measurementConfig.getExecutionConfig(), measurementConfig);
      TestExecutor executor = ExecutorCreator.createExecutor(folders, testTransformer, env);
      ModuleClassMapping mapping = new ModuleClassMapping(folders.getProjectFolder(), executor.getModules());
      final AnalyseFullData afd = new AnalyseFullData(changefile, statistics, mapping);
      afd.analyseFolder(measurementFolder);
      Constants.OBJECTMAPPER.writeValue(new File(localFolder, "statistics.json"), statistics);
   }

   public String getLatestVersion() {
      return version;
   }

   public PeassFolders getFolders() {
      return folders;
   }

   public String getVersionOld() {
      return versionOld;
   }

   public File getProjectFolder() {
      return folders.getProjectFolder();
   }

   public File getFullResultsVersion() {
      final File fullResultsVersion = new File(localFolder, version + "_" + versionOld);
      return fullResultsVersion;
   }
}
