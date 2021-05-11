package de.dagere.peass.dependency.jmh;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.xml.bind.JAXBException;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import de.dagere.kopeme.datacollection.TimeDataCollectorNoGC;
import de.dagere.kopeme.datastorage.XMLDataLoader;
import de.dagere.kopeme.datastorage.XMLDataStorer;
import de.dagere.kopeme.generated.Kopemedata;
import de.dagere.kopeme.generated.Kopemedata.Testcases;
import de.dagere.kopeme.generated.Result;
import de.dagere.kopeme.generated.Result.Fulldata;
import de.dagere.kopeme.generated.Result.Fulldata.Value;
import de.dagere.kopeme.generated.Result.Params;
import de.dagere.kopeme.generated.Result.Params.Param;
import de.dagere.kopeme.generated.TestcaseType;
import de.dagere.kopeme.generated.TestcaseType.Datacollector;
import de.dagere.peass.config.MeasurementConfiguration;
import de.dagere.peass.utils.Constants;

public class JmhKoPeMeConverter {
   private static final int SECONDS_TO_NANOSECONDS = 1000000000;

   private final MeasurementConfiguration measurementConfig;

   public JmhKoPeMeConverter(final MeasurementConfiguration measurementConfig) {
      this.measurementConfig = measurementConfig;
   }

   public void convertToXMLData(final File sourceJsonResultFile, final File clazzResultFolder) {
      try {
         JsonNode rootNode = Constants.OBJECTMAPPER.readTree(sourceJsonResultFile);
         if (rootNode != null && rootNode instanceof ArrayNode) {
            ArrayNode benchmarks = (ArrayNode) rootNode;
            for (JsonNode benchmark : benchmarks) {

               final String name = benchmark.get("benchmark").asText();
               String benchmarkMethodName = name.substring(name.lastIndexOf('.') + 1);

               JsonNode primaryMetric = benchmark.get("primaryMetric");
               ArrayNode rawData = (ArrayNode) primaryMetric.get("rawData");

               File koPeMeFile = new File(clazzResultFolder, benchmarkMethodName + ".xml");
               Kopemedata transformed;
               Datacollector timeCollector;
               if (koPeMeFile.exists()) {
                  transformed = XMLDataLoader.loadData(koPeMeFile);
                  timeCollector = transformed.getTestcases().getTestcase().get(0).getDatacollector().get(0);
               } else {
                  transformed = new Kopemedata();
                  Testcases testcases = new Testcases();
                  testcases.setClazz(name.substring(0, name.lastIndexOf('.')));
                  transformed.setTestcases(testcases);
                  TestcaseType testclazz = new TestcaseType();
                  transformed.getTestcases().getTestcase().add(testclazz);
                  testclazz.setName(name.substring(name.lastIndexOf('.') + 1));
                  timeCollector = new Datacollector();
                  timeCollector.setName(TimeDataCollectorNoGC.class.getName());
                  testclazz.getDatacollector().add(timeCollector);
               }

               for (JsonNode vmExecution : rawData) {
                  Result result = buildResult(vmExecution);
                  JsonNode params = benchmark.get("params");
                  if (params != null) {
                     setParamMap(result, params);
                  }
                  timeCollector.getResult().add(result);
               }

               XMLDataStorer.storeData(koPeMeFile, transformed);
            }
         }
      } catch (IOException | JAXBException e) {
         throw new RuntimeException(e);
      }
   }

   private void setParamMap(final Result result, final JsonNode params) {
      result.setParams(new Params());
      for (Iterator<String> fieldIterator = params.fieldNames(); fieldIterator.hasNext();) {
         final String field = fieldIterator.next();
         final String value = params.get(field).asText();
         final Param param = new Param();
         param.setKey(field);
         param.setValue(value);
         result.getParams().getParam().add(param);
      }
   }

   private Result buildResult(final JsonNode vmExecution) {
      Result result = new Result();
      result.setFulldata(new Fulldata());
      for (JsonNode iteration : vmExecution) {
         Value value = new Value();
         long iterationDuration = (long) (iteration.asDouble() * SECONDS_TO_NANOSECONDS);
         value.setValue(iterationDuration);
         result.getFulldata().getValue().add(value);
      }

      DescriptiveStatistics statistics = new DescriptiveStatistics();
      result.getFulldata().getValue().forEach(value -> statistics.addValue(value.getValue()));
      result.setValue(statistics.getMean());
      result.setDeviation(statistics.getStandardDeviation());
      result.setIterations(result.getFulldata().getValue().size());

      // Assume that warmup and repetitions took place as defined, since they are not recorded by jmh
      result.setWarmup(measurementConfig.getWarmup());
      result.setRepetitions(measurementConfig.getRepetitions());

      return result;
   }
}