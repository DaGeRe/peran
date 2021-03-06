package de.dagere.peass.ci;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import javax.xml.bind.JAXBException;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import de.dagere.peass.config.MeasurementConfiguration;
import de.dagere.peass.dependency.PeassFolders;
import de.dagere.peass.dependency.analysis.data.TestCase;
import de.dagere.peass.dependency.execution.EnvironmentVariables;
import de.dagere.peass.dependencyprocessors.AdaptiveTester;

public class ContinuousMeasurementExecutor {

   private static final Logger LOG = LogManager.getLogger(ContinuousMeasurementExecutor.class);

   private final String version, versionOld;
   private final PeassFolders folders;
   private final MeasurementConfiguration measurementConfig;
   private final EnvironmentVariables env;

   public ContinuousMeasurementExecutor(final String version, final String versionOld, final PeassFolders folders, final MeasurementConfiguration measurementConfig,
         final EnvironmentVariables env) {
      this.version = version;
      this.versionOld = versionOld;
      this.folders = folders;
      this.measurementConfig = measurementConfig;
      this.env = env;
   }

   public File executeMeasurements(final Set<TestCase> tests, final File fullResultsVersion) throws IOException, InterruptedException, JAXBException, XmlPullParserException {
      if (!fullResultsVersion.exists()) {
         if (measurementConfig.getExecutionConfig().isRedirectSubprocessOutputToFile()) {
            File logFile = new File(fullResultsVersion.getParentFile(), "measurement_" + version + "_" + versionOld + ".txt");
            LOG.info("Executing measurement - Log goes to {}", logFile.getAbsolutePath());
            try (LogRedirector director = new LogRedirector(logFile)) {
               doMeasurement(tests, fullResultsVersion);
            }
         } else {
            doMeasurement(tests, fullResultsVersion);
         }

      } else {
         LOG.info("Skipping measurement - result folder {} already existing", fullResultsVersion.getAbsolutePath());
      }
      final File measurementFolder = new File(fullResultsVersion, "measurements");
      return measurementFolder;
   }

   private void doMeasurement(final Set<TestCase> tests, final File fullResultsVersion) throws IOException, InterruptedException, JAXBException, XmlPullParserException {
      MeasurementConfiguration copied = createCopiedConfiguration();
      
      cleanTemporaryFolders();

      final AdaptiveTester tester = new AdaptiveTester(folders, copied, env);
      for (final TestCase test : tests) {
         tester.evaluate(test);
      }

      final File fullResultsFolder = folders.getFullMeasurementFolder();
      LOG.debug("Moving to: {}", fullResultsVersion.getAbsolutePath());
      FileUtils.moveDirectory(fullResultsFolder, fullResultsVersion);
   }

   private void cleanTemporaryFolders() throws IOException {
      final File fullResultsFolder = folders.getFullMeasurementFolder();
      FileUtils.deleteDirectory(fullResultsFolder);
      fullResultsFolder.mkdirs();
      folders.getDetailResultFolder().mkdirs();
      FileUtils.deleteDirectory(folders.getTempMeasurementFolder());
      folders.getTempMeasurementFolder().mkdirs();
   }

   private MeasurementConfiguration createCopiedConfiguration() {
      MeasurementConfiguration copied = new MeasurementConfiguration(measurementConfig);
      copied.setUseKieker(false);
      copied.setVersion(version);
      copied.setVersionOld(versionOld);
      return copied;
   }
}
