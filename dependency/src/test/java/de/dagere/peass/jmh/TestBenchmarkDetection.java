package de.dagere.peass.jmh;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.Assert;
import org.junit.Test;

import de.dagere.peass.config.MeasurementConfiguration;
import de.dagere.peass.dependency.analysis.ModuleClassMapping;
import de.dagere.peass.dependency.analysis.data.TestCase;
import de.dagere.peass.dependency.analysis.data.TestSet;
import de.dagere.peass.dependency.execution.ProjectModules;
import de.dagere.peass.dependency.jmh.JmhTestTransformer;

public class TestBenchmarkDetection {
   @Test
   public void testBenchmarkDetection() throws FileNotFoundException, IOException, XmlPullParserException {
      JmhTestTransformer jmhTransformer = new JmhTestTransformer(JmhTestConstants.BASIC_VERSION, new MeasurementConfiguration(3));
      ProjectModules modules = new ProjectModules(JmhTestConstants.BASIC_VERSION);
      TestSet tests = jmhTransformer.findModuleTests(new ModuleClassMapping(JmhTestConstants.BASIC_VERSION, modules), null,
            modules);
      
      Assert.assertEquals(tests.getTests().size(), 1);
      TestCase test = tests.getTests().iterator().next();
      Assert.assertEquals("de.dagere.peass.ExampleBenchmark#testMethod", test.getExecutable());
   }
   
   @Test
   public void testBenchmarkDetectionInnerClass() throws FileNotFoundException, IOException, XmlPullParserException {
      JmhTestTransformer jmhTransformer = new JmhTestTransformer(JmhTestConstants.INNER_CLASS_VERSION, new MeasurementConfiguration(3));
      ProjectModules modules = new ProjectModules(JmhTestConstants.INNER_CLASS_VERSION);
      TestSet tests = jmhTransformer.findModuleTests(new ModuleClassMapping(JmhTestConstants.INNER_CLASS_VERSION, modules), null,
            modules);
      
      Assert.assertEquals(tests.getTests().size(), 1);
      TestCase test = tests.getTests().iterator().next();
      Assert.assertEquals("de.dagere.peass.ExampleBenchmark#testMethod", test.getExecutable());
   }

   @Test
   public void testMultimoduleBenchmarkDetection() throws FileNotFoundException, IOException, XmlPullParserException {
      JmhTestTransformer jmhTransformer = new JmhTestTransformer(JmhTestConstants.MULTIMODULE_VERSION, new MeasurementConfiguration(3));
      ProjectModules modules = new ProjectModules(Arrays.asList(new File[] { new File(JmhTestConstants.MULTIMODULE_VERSION, "base-module"),
            new File(JmhTestConstants.MULTIMODULE_VERSION, "using-module") }));
      ModuleClassMapping mapping = new ModuleClassMapping(JmhTestConstants.MULTIMODULE_VERSION, modules);
      TestSet tests = jmhTransformer.findModuleTests(mapping, null, modules);
      Iterator<TestCase> iterator = tests.getTests().iterator();
      TestCase test = iterator.next();
      Assert.assertEquals("de.dagere.peass.ExampleBenchmarkBasic#testMethod", test.getExecutable());

      TestCase testUsing = iterator.next();
      Assert.assertEquals("de.dagere.peass.ExampleBenchmarkUsing#testMethod", testUsing.getExecutable());
   }
}
