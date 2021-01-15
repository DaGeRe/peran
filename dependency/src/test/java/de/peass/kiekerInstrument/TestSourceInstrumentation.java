package de.peass.kiekerInstrument;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import de.peass.TestConstants;
import de.peass.dependency.execution.AllowedKiekerRecord;

public class TestSourceInstrumentation {

   @Test
   public void testSingleClass() throws IOException {
      TestConstants.CURRENT_FOLDER.mkdirs();

      File testFile = SourceInstrumentationTestUtil.copyResource("src/main/java/de/peass/C0_0.java", "/sourceInstrumentation/project_2/");

      InstrumentKiekerSource instrumenter = new InstrumentKiekerSource(AllowedKiekerRecord.OPERATIONEXECUTION);
      instrumenter.instrument(testFile);

      testFileIsInstrumented(testFile, "public void de.peass.C0_0.method0()", "OperationExecutionRecord");
   }

   public static void testFileIsInstrumented(File testFile, String fqn, String recordName) throws IOException {
      String changedSource = FileUtils.readFileToString(testFile, StandardCharsets.UTF_8);

      Assert.assertThat(changedSource, Matchers.containsString("import kieker.monitoring.core.controller.MonitoringController;"));
      Assert.assertThat(changedSource, Matchers.containsString("import kieker.monitoring.core.registry.ControlFlowRegistry;"));
      Assert.assertThat(changedSource, Matchers.containsString("import kieker.monitoring.core.registry.SessionRegistry;"));

      Assert.assertThat(changedSource, Matchers.containsString("signature = \"" + fqn));
      Assert.assertThat(changedSource, Matchers.containsString("new " + recordName));
   }

   @Test
   public void testProjectInstrumentation() throws IOException {
      SourceInstrumentationTestUtil.initProject("/sourceInstrumentation/project_2/");

      InstrumentKiekerSource instrumenter = new InstrumentKiekerSource(AllowedKiekerRecord.OPERATIONEXECUTION);
      instrumenter.instrumentProject(TestConstants.CURRENT_FOLDER);

      testFileIsInstrumented(new File(TestConstants.CURRENT_FOLDER, "src/main/java/de/peass/C0_0.java"), "public void de.peass.C0_0.method0()", "OperationExecutionRecord");
      testFileIsInstrumented(new File(TestConstants.CURRENT_FOLDER, "src/main/java/de/peass/C1_0.java"), "public void de.peass.C1_0.method0()", "OperationExecutionRecord");
      testFileIsInstrumented(new File(TestConstants.CURRENT_FOLDER, "src/main/java/de/peass/AddRandomNumbers.java"), "public int de.peass.AddRandomNumbers.getValue()",
            "OperationExecutionRecord");
      testFileIsInstrumented(new File(TestConstants.CURRENT_FOLDER, "/src/test/java/de/peass/MainTest.java"), "public void de.peass.MainTest.testMe()", "OperationExecutionRecord");
      testFileIsInstrumented(new File(TestConstants.CURRENT_FOLDER, "/src/test/java/de/peass/MainTest.java"), "public new de.peass.MainTest.<init>()", "OperationExecutionRecord");
   }

   @Test
   public void testDifferentSignatures() throws IOException {
      SourceInstrumentationTestUtil.initProject("/sourceInstrumentation/project_2_signatures/");

      InstrumentKiekerSource instrumenter = new InstrumentKiekerSource(AllowedKiekerRecord.REDUCED_OPERATIONEXECUTION);
      instrumenter.instrumentProject(TestConstants.CURRENT_FOLDER);

      String changedSource = FileUtils.readFileToString(new File(TestConstants.CURRENT_FOLDER, "src/main/java/de/peass/C0_0.java"), StandardCharsets.UTF_8);

      Assert.assertThat(changedSource, Matchers.containsString("signature = \"public void de.peass.C0_0.method0(int)\""));
      Assert.assertThat(changedSource, Matchers.containsString("signature = \"public java.lang.String de.peass.C0_0.method0(java.lang.String)\""));
      Assert.assertThat(changedSource, Matchers.containsString("signature = \"public static void de.peass.C0_0.myStaticStuff()\""));
      Assert.assertThat(changedSource, Matchers.containsString("signature = \"public new de.peass.C0_0$MyInnerClass.<init>()\""));
      Assert.assertThat(changedSource, Matchers.containsString("signature = \"public void de.peass.C0_0$MyInnerClass.innerMethod()\""));
   }
}
