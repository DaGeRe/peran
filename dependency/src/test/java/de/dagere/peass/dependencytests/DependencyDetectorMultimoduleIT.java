package de.dagere.peass.dependencytests;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsCollectionContaining;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.github.javaparser.ParseException;

import co.unruly.matchers.StreamMatchers;
import de.dagere.peass.config.ExecutionConfig;
import de.dagere.peass.dependency.ChangeManager;
import de.dagere.peass.dependency.PeassFolders;
import de.dagere.peass.dependency.analysis.data.ChangedEntity;
import de.dagere.peass.dependency.analysis.data.TestSet;
import de.dagere.peass.dependency.changesreading.ClazzChangeData;
import de.dagere.peass.dependency.execution.EnvironmentVariables;
import de.dagere.peass.dependency.persistence.InitialDependency;
import de.dagere.peass.dependency.reader.DependencyReader;
import de.dagere.peass.dependencyprocessors.ViewNotFoundException;
import de.dagere.peass.dependencytests.helper.FakeFileIterator;
import de.dagere.peass.vcs.VersionIterator;

public class DependencyDetectorMultimoduleIT {

   private static final Logger LOG = LogManager.getLogger(DependencyDetectorMultimoduleIT.class);

   private static final File VERSIONS_FOLDER = new File("src/test/resources/dependencyIT_multimodule");
   private static final File BASIC_STATE = new File(VERSIONS_FOLDER, "basic_state");

   // private DependencyManager handler;

   @Before
   public void initialize() throws IOException, InterruptedException {
      Assert.assertTrue(VERSIONS_FOLDER.exists());

      FileUtils.deleteDirectory(DependencyTestConstants.CURRENT);
      FileUtils.copyDirectory(BASIC_STATE, DependencyTestConstants.CURRENT);

      // handler = new DependencyManager(CURRENT);
      // final boolean success = handler.initialyGetTraces();
      //
      // Assert.assertTrue(success);
   }

   // @org.junit.After
   // public void cleanAfterwards() throws IOException {
   // FileUtils.deleteDirectory(CURRENT);
   // // be aware: maven does not compile if a .class-file is still in the resources, since it gets identified as test
   // }

   @Test
   public void testNormalChange() throws IOException, InterruptedException, XmlPullParserException, ParseException, ViewNotFoundException {
      final File secondVersion = new File(VERSIONS_FOLDER, "normal_change");

      final VersionIterator fakeIterator = new FakeFileIterator(DependencyTestConstants.CURRENT, Arrays.asList(secondVersion));

      final Map<ChangedEntity, ClazzChangeData> changes = DependencyDetectorTestUtil.buildChanges("base-module", "de.dagere.base.BaseChangeable", "doSomething");

      ChangeManager changeManager = Mockito.mock(ChangeManager.class);
      Mockito.when(changeManager.getChanges(Mockito.any())).thenReturn(changes);
      
      final DependencyReader reader = new DependencyReader(DependencyTestConstants.DEFAULT_CONFIG_NO_VIEWS, new PeassFolders(DependencyTestConstants.CURRENT), 
            DependencyTestConstants.NULL_RESULTS_FOLDERS, null, fakeIterator, changeManager, new ExecutionConfig(5), new EnvironmentVariables());

      final boolean success = reader.readInitialVersion();
      Assert.assertTrue(success);

      LOG.debug(reader.getDependencies().getInitialversion().getInitialDependencies());
      final InitialDependency dependency = reader.getDependencies().getInitialversion().getInitialDependencies()
            .get(new ChangedEntity("de.AnotherTest", "using-module", "testMeAlso"));
      LOG.debug(dependency.getEntities());
      Assert.assertThat(dependency.getEntities(), IsCollectionContaining.hasItem(new ChangedEntity("de.dagere.base.BaseChangeable", "base-module", "doSomething")));

      fakeIterator.goToNextCommit();
      reader.analyseVersion(changeManager);

      final TestSet foundDependency = DependencyDetectorTestUtil.findDependency(reader.getDependencies(), "base-module§de.dagere.base.BaseChangeable#doSomething",
            DependencyTestConstants.VERSION_1);
      testBaseChangeEffect(foundDependency);
   }

   @Test
   public void testTwoChanges()
         throws IOException, XmlPullParserException, InterruptedException, ParseException, ViewNotFoundException {
      final File thirdVersion = new File(VERSIONS_FOLDER, "another_change");

      final VersionIterator fakeIterator = new FakeFileIterator(DependencyTestConstants.CURRENT, Arrays.asList(thirdVersion));

      final Map<ChangedEntity, ClazzChangeData> changes = DependencyDetectorTestUtil.buildChanges("base-module", "de.dagere.base.BaseChangeable", "doSomething");
      DependencyDetectorTestUtil.addChange(changes, "base-module", "de.dagere.base.NextBaseChangeable", "doSomething");

      ChangeManager changeManager = Mockito.mock(ChangeManager.class);
      Mockito.when(changeManager.getChanges(Mockito.any())).thenReturn(changes);
      final DependencyReader reader = new DependencyReader(DependencyTestConstants.DEFAULT_CONFIG_NO_VIEWS, new PeassFolders(DependencyTestConstants.CURRENT), DependencyTestConstants.NULL_RESULTS_FOLDERS, null, fakeIterator, changeManager, new ExecutionConfig(5), new EnvironmentVariables());
      final boolean success = reader.readInitialVersion();
      Assert.assertTrue(success);

      fakeIterator.goToNextCommit();
      reader.analyseVersion(changeManager);

      final TestSet foundDependency2 = DependencyDetectorTestUtil.findDependency(reader.getDependencies(), "base-module§de.dagere.base.BaseChangeable#doSomething",
            DependencyTestConstants.VERSION_1);
      testBaseChangeEffect(foundDependency2);

      final TestSet foundDependency3 = DependencyDetectorTestUtil.findDependency(reader.getDependencies(), "base-module§de.dagere.base.NextBaseChangeable#doSomething",
            DependencyTestConstants.VERSION_1);
      Assert.assertThat(foundDependency3.getTests().stream(), StreamMatchers.anyMatch(
            Matchers.allOf(Matchers.hasProperty("clazz", Matchers.is("de.NextTest")),
                  Matchers.hasProperty("method", Matchers.isOneOf("nextTestMe", "nextTestMeAlso")))));
   }

   

   private void testBaseChangeEffect(final TestSet foundDependency) {

      System.out.println(foundDependency.getTestcases());

      Assert.assertThat(foundDependency.getTests().stream(), StreamMatchers.anyMatch(
            Matchers.allOf(Matchers.hasProperty("clazz", Matchers.is("de.dagere.base.BaseTest")),
                  Matchers.hasProperty("method", Matchers.isOneOf("testMe", "testMeAlso")))));

      Assert.assertThat(foundDependency.getTests().stream(), StreamMatchers.anyMatch(
            Matchers.allOf(Matchers.hasProperty("clazz", Matchers.is("de.NextTest")),
                  Matchers.hasProperty("method", Matchers.is("nextTestMe")))));

      Assert.assertThat(foundDependency.getTests().stream(), StreamMatchers.anyMatch(
            Matchers.allOf(Matchers.hasProperty("clazz", Matchers.is("de.AnotherTest")),
                  Matchers.hasProperty("method", Matchers.isOneOf("testMeAlso")))));
   }
}
