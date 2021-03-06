package de.dagere.peass.dependency.parallel;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import com.github.javaparser.ParseException;

import de.dagere.peass.dependency.reader.DependencyReader;
import de.dagere.peass.dependency.reader.FirstRunningVersionFinder;
import de.dagere.peass.dependencyprocessors.VersionComparator;
import de.dagere.peass.dependencyprocessors.ViewNotFoundException;
import de.dagere.peass.vcs.GitCommit;
import de.dagere.peass.vcs.VersionIterator;

public final class OneReader implements Runnable {

   private static final Logger LOG = LogManager.getLogger(OneReader.class);

   private final GitCommit minimumCommit;
   private final VersionIterator reserveIterator;
   final FirstRunningVersionFinder firstRunningVersionFinder;
   private final DependencyReader reader;

   public OneReader(final GitCommit minimumCommit, final VersionIterator reserveIterator, final DependencyReader reader,
         final FirstRunningVersionFinder firstRunningVersionFinder) {
      this.minimumCommit = minimumCommit;
      this.reserveIterator = reserveIterator;
      this.firstRunningVersionFinder = firstRunningVersionFinder;
      this.reader = reader;
   }

   @Override
   public void run() {
      try {
         boolean init = firstRunningVersionFinder.searchFirstRunningCommit();
         if (init) {
            LOG.debug("Reader initalized: " + reader + " This: " + this);
            if (!reader.readInitialVersion()) {
               LOG.error("Analyzing first version was not possible");
            } else {
               final boolean readingSuccess = reader.readDependencies();
               if (readingSuccess) {
                  readRemaining(reader);
               }
            }
         }
      } catch (final Throwable e) {
         e.printStackTrace();
      }
   }

   private void readRemaining(final DependencyReader reader) throws FileNotFoundException, IOException, XmlPullParserException, InterruptedException, ParseException, ViewNotFoundException {
      String newest = reader.getDependencies().getNewestVersion();
      reader.setIterator(reserveIterator);
      while (reserveIterator.hasNextCommit() && VersionComparator.isBefore(newest, minimumCommit.getTag())) {
         reserveIterator.goToNextCommit();
         LOG.debug("Remaining: {} This: {}", reserveIterator.getTag(), this);
         reader.readVersion();
         newest = reader.getDependencies().getNewestVersion();
      }
   }
}