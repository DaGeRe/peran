package de.dagere.peass.dependency;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import de.dagere.peass.config.MeasurementConfiguration;
import de.dagere.peass.dependency.execution.EnvironmentVariables;
import de.dagere.peass.execution.processutils.ProcessBuilderHelper;
import de.dagere.peass.testtransformation.JUnitTestTransformer;

//TODO Fix test by creating MavenTestExecutor and mocking the creation of the ProcessBuilderHelper with PowerMock
@Ignore
public class TestMavenTestExecutor {
   
   @Test
   public void testParameterConcatenation() throws IOException, XmlPullParserException, InterruptedException {
      JUnitTestTransformer testTransformerMock = Mockito.mock(JUnitTestTransformer.class);
      Mockito.when(testTransformerMock.getConfig()).thenReturn(new MeasurementConfiguration(5));
      
      PeassFolders foldersMock = Mockito.mock(PeassFolders.class);
      Mockito.when(foldersMock.getTempDir()).thenReturn(new File("/tmp/test2"));
      Mockito.when(foldersMock.getTempMeasurementFolder()).thenReturn(new File("/tmp/test2"));
      
      ProcessBuilderHelper helper = new ProcessBuilderHelper(new EnvironmentVariables("-Pvar1=1 -Pvar5=asd"), foldersMock);
//      MavenTestExecutor executor = new MavenTestExecutor(foldersMock, 
//            testTransformerMock, 
//            new EnvironmentVariables("-Pvar1=1 -Pvar5=asd"));
      ProcessBuilderHelper testExecutor = Mockito.spy(helper);
      
      testExecutor.buildFolderProcess(new File("/tmp/test"), new File("/tmp/log"), new String[] {"addition1", "addition2", "addition3"});
      
      ArgumentCaptor<String[]> parametersCaptor = ArgumentCaptor.forClass(String[].class);
      Mockito.verify(testExecutor).buildFolderProcess(Mockito.any(), Mockito.any(), parametersCaptor.capture());
      
      String[] capturedValue = parametersCaptor.getValue();
      System.out.println("Captured: " + Arrays.toString(capturedValue));
      Assert.assertEquals("test", capturedValue[1]);
      Assert.assertEquals("-Djava.io.tmpdir=/tmp/test2", capturedValue[3]);
      Assert.assertEquals("-DfailIfNoTests=false", capturedValue[10]);
      Assert.assertEquals("addition1", capturedValue[11]);
      Assert.assertEquals("addition3", capturedValue[13]);
   }
}
