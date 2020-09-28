package de.peass.measurement.rca;

import java.io.IOException;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import de.peass.dependencyprocessors.ViewNotFoundException;
import de.peass.measurement.rca.data.CallTreeNode;
import de.peass.measurement.rca.helper.TreeBuilder;
import kieker.analysis.exception.AnalysisConfigurationException;

public class CauseTesterMockUtil {

   public static void mockMeasurement(CauseTester measurer, final TreeBuilder builderPredecessor)
         throws IOException, XmlPullParserException, InterruptedException, ViewNotFoundException, AnalysisConfigurationException, JAXBException {
      Mockito.doAnswer(new Answer<Void>() {
         @Override
         public Void answer(InvocationOnMock invocation) throws Throwable {
            Object[] args = invocation.getArguments();
            System.out.println("Mocking measurement of: " + args[0]);
            List<CallTreeNode> nodes = (List<CallTreeNode>) args[0] ;
            builderPredecessor.buildMeasurements(nodes.toArray(new CallTreeNode[0]));
            return null;
         }
      }).when(measurer).measureVersion(Mockito.any());
   }

}