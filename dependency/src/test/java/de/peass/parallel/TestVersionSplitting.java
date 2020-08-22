package de.peass.parallel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import de.peass.dependency.ChangeManager;
import de.peass.dependency.execution.TestExecutor;
import de.peass.dependency.parallel.Merger;
import de.peass.dependency.parallel.OneReader;
import de.peass.dependency.persistence.Dependencies;
import de.peass.dependency.persistence.InitialVersion;
import de.peass.dependency.reader.DependencyReader;
import de.peass.dependencytests.helper.FakeVersionIterator;
import de.peass.vcs.GitCommit;
import de.peass.vcs.VersionIterator;

public class TestVersionSplitting {

   @Rule
   public TemporaryFolder testFolder = new TemporaryFolder(new File("target"));

   static class DummyReader extends DependencyReader {

      public DummyReader(File dummyFolder, VersionIterator iterator, ChangeManager manager) throws IOException {
         super(dummyFolder, null, null, iterator, 1, manager);
      }

      @Override
      public boolean searchFirstRunningCommit(VersionIterator iterator, TestExecutor executor, File projectFolder) {
         return true;
      }

      @Override
      public boolean readInitialVersion() throws IOException, InterruptedException, XmlPullParserException {
         dependencyResult.setInitialversion(new InitialVersion());
         dependencyResult.getInitialversion().setVersion(iterator.getTag());
         return true;
      }

      static Set<String> nonRunning = new HashSet<>(Arrays.asList("4", "5"));

      @Override
      public void readVersion() throws IOException, FileNotFoundException {
         System.out.println(nonRunning + " " + iterator.getTag() + " " + nonRunning.contains(iterator.getTag()));
         if (!nonRunning.contains(iterator.getTag())) {
            dependencyResult.getVersions().put(iterator.getTag(), null);
            System.out.println("Reading: " + iterator.getTag());
         }
      }
   }

   @Before
   public void init() {
      DummyReader.nonRunning = new HashSet<>(Arrays.asList("4", "5"));
   }

   @Test
   public void testSplittingNonRunning() throws IOException {
      List<GitCommit> commits = ParallelTestUtil.getCommits();
      int count = 3;
      int size = commits.size() > 2 * count ? commits.size() / count : 2;

      for (int i = 1; i < 10; i++) {
         for (int j = i + 1; j < 10; j++) {
            DummyReader.nonRunning = new HashSet<>(Arrays.asList(String.valueOf(i), String.valueOf(j)));

            List<Dependencies> dependencies = new LinkedList<>();
            for (int chunk = 0; chunk < count; chunk++) {
               final int max = Math.min((chunk + 1) * size + 3, commits.size());// Assuming one in three commits should contain a source-change
               readUntilMax(commits, dependencies, chunk, chunk * size, max);
            }

            Dependencies merged = Merger.mergeDependencies(dependencies);

            System.out.println(merged.getVersions().keySet() + " " + merged.getVersions().size());
            Assert.assertEquals("Error in " + DummyReader.nonRunning, 7, merged.getVersions().size());
         }
      }
   }

   @Test
   public void testSplitting() throws IOException {
      List<GitCommit> commits = ParallelTestUtil.getCommits();

      int count = 3;
      int size = commits.size() > 2 * count ? commits.size() / count : 2;

      List<Dependencies> dependencies = new LinkedList<>();
      for (int chunk = 0; chunk < count; chunk++) {
         final int max = Math.min((chunk + 1) * size + 3, commits.size());// Assuming one in three commits should contain a source-change
         readUntilMax(commits, dependencies, chunk, chunk * size, max);
      }

      Dependencies merged = Merger.mergeDependencies(dependencies);

      System.out.println(merged.getVersions().keySet());
      Assert.assertEquals(7, merged.getVersions().size());
   }

   @Test
   public void testSplittingStrangeDistribution() throws IOException {
      List<GitCommit> commits = ParallelTestUtil.getCommits();

      List<Dependencies> dependencies = new LinkedList<>();
      readUntilMax(commits, dependencies, 0, 0, 6);
      readUntilMax(commits, dependencies, 1, 6, 8);
      readUntilMax(commits, dependencies, 2, 7, 10);

      Dependencies merged = Merger.mergeDependencies(dependencies);

      System.out.println(merged.getVersions().keySet());
      Assert.assertEquals(7, merged.getVersions().size());
   }

   private void readUntilMax(List<GitCommit> commits, List<Dependencies> dependencies, int i, int min, final int max) throws IOException {
      final List<GitCommit> currentCommits = commits.subList(min, max);
      final List<GitCommit> reserveCommits = commits.subList(max - 1, commits.size());
      final GitCommit minimumCommit = commits.get(Math.min(max, commits.size() - 1));
      System.out.println("Minimum: " + minimumCommit.getTag());
      readDummyDependencies(dependencies, i, currentCommits, reserveCommits, minimumCommit);
   }

   private void readDummyDependencies(List<Dependencies> dependencies, int i, final List<GitCommit> currentCommits, final List<GitCommit> reserveCommits,
         final GitCommit minimumCommit)
         throws IOException {
      File dummyFolder = new File(testFolder.getRoot(), "part_" + i);
      dummyFolder.mkdir();
      File pom = new File(dummyFolder, "pom.xml");
      try (BufferedWriter newBufferedWriter = Files.newBufferedWriter(pom.toPath())) {
         newBufferedWriter.write("<project></project>");
      }

      final VersionIterator fakeIterator = new FakeVersionIterator(dummyFolder, currentCommits);
      final ChangeManager changeManager = Mockito.mock(ChangeManager.class);
      Mockito.when(changeManager.getChanges(Mockito.any())).thenReturn(null);

      DummyReader dummy = new DummyReader(dummyFolder, fakeIterator, changeManager);
      System.out.println(minimumCommit.getTag());
      final VersionIterator reserveIterator = new FakeVersionIterator(dummyFolder, reserveCommits);
      OneReader reader = new OneReader(minimumCommit, new File("/dev/null"), reserveIterator, dummy);
      reader.run();

      dependencies.add(dummy.getDependencies());
   }

}