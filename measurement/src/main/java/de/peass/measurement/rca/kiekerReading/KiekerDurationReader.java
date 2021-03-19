package de.peass.measurement.rca.kiekerReading;

import java.io.File;
import java.util.Set;

import de.peass.config.MeasurementConfiguration;
import de.peass.dependency.analysis.ModuleClassMapping;
import de.peass.dependency.analysis.data.TestCase;
import de.peass.measurement.rca.data.CallTreeNode;
import de.peass.measurement.rca.kieker.TreeStage;
import teetime.framework.Execution;

public class KiekerDurationReader {

   public static void executeDurationStage(final File kiekerTraceFolder, final Set<CallTreeNode> measuredNodes, final String version) {
      KiekerReaderConfigurationDuration configuration = new KiekerReaderConfigurationDuration();
      configuration.readDurations(kiekerTraceFolder, measuredNodes, version);
      
      Execution execution = new Execution(configuration);
      execution.executeBlocking();
   }
   
   public static TreeStage executeDurationStage(final File kiekerTraceFolder, final String prefix, final TestCase test, 
         final boolean ignoreEOIs, final MeasurementConfiguration config, final ModuleClassMapping mapping) {
      KiekerReaderConfigurationDuration configuration = new KiekerReaderConfigurationDuration();
      TreeStage stage = configuration.readTree(kiekerTraceFolder,  prefix, test, ignoreEOIs,  config, mapping);
      
      Execution execution = new Execution(configuration);
      execution.executeBlocking();
      
      return stage;
   }

}