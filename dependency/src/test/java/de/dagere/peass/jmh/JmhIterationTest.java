package de.dagere.peass.jmh;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import javax.xml.bind.JAXBException;

import org.apache.commons.io.FileUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.github.javaparser.ParseException;

import de.dagere.kopeme.datastorage.XMLDataLoader;
import de.dagere.kopeme.generated.Kopemedata;
import de.dagere.peass.TestConstants;
import de.dagere.peass.TestUtil;
import de.dagere.peass.config.MeasurementConfiguration;
import de.dagere.peass.dependency.ExecutorCreator;
import de.dagere.peass.dependency.PeASSFolders;
import de.dagere.peass.dependency.analysis.data.TestCase;
import de.dagere.peass.dependency.execution.EnvironmentVariables;
import de.dagere.peass.dependency.execution.TestExecutor;
import de.dagere.peass.dependency.jmh.JMHTestTransformer;
import de.dagere.peass.dependencyprocessors.ViewNotFoundException;

@Ignore
public class JmhIterationTest {
   
   @Before
   public void clearCurrent() throws IOException {
      TestUtil.deleteContents(TestConstants.CURRENT_FOLDER.getParentFile());
   }
   
   @Test
   public void testVersionReading() throws IOException, InterruptedException, XmlPullParserException, ParseException, ViewNotFoundException, ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, JAXBException {
      FileUtils.copyDirectory(JmhTestConstants.BASIC_VERSION, TestConstants.CURRENT_FOLDER);
      
      MeasurementConfiguration measurementConfig = new MeasurementConfiguration(3);
      measurementConfig.setIterations(4);
      measurementConfig.setWarmup(2);
      JMHTestTransformer transformer = new JMHTestTransformer(TestConstants.CURRENT_FOLDER, measurementConfig);
      PeASSFolders folders = new PeASSFolders(TestConstants.CURRENT_FOLDER);
      TestExecutor executor = ExecutorCreator.createExecutor(folders, transformer, new EnvironmentVariables());
    
//      File logFile = new File(folders.getLogFolder(), "test.txt");
      TestCase testcase = new TestCase("de.dagere.peass.ExampleBenchmark#testMethod");
      executor.executeTest(testcase, folders.getLogFolder(), 10);
      
      File clazzFolder = folders.findTempClazzFolder(testcase).get(0);
      Kopemedata data = XMLDataLoader.loadData(new File(clazzFolder, testcase.getMethod() + ".xml"));
   }

}