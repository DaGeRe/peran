package de.peass.dependency.traces;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.peass.dependency.ClazzFileFinder;
import de.peass.dependency.analysis.data.ChangedEntity;
import de.peass.dependency.analysis.data.TestCase;
import de.peass.dependency.traces.requitur.content.RuleContent;

public class TraceWriter {

   private static final Logger LOG = LogManager.getLogger(TraceWriter.class);
   
   private final String version;
   private final TestCase testcase;
   private final File viewFolder;
   
   public TraceWriter(String version, TestCase testcase, File viewFolder) {
      this.version = version;
      this.testcase = testcase;
      this.viewFolder = viewFolder;
   }

   public void writeTrace(final String versionCurrent, final long sizeInMB, final TraceMethodReader traceMethodReader, final TraceWithMethods trace, List<File> traceFiles)
         throws IOException {
      final File methodDir = new File(getClazzDir(version, testcase), testcase.getMethod());
      if (!methodDir.exists()) {
         methodDir.mkdir(); 
      }
      String shortVersion = versionCurrent.substring(0, 6);
      if (versionCurrent.endsWith("~1")) {
         shortVersion = shortVersion + "~1";
      }
      final File methodTrace = writeTraces(sizeInMB, traceMethodReader, trace, traceFiles, methodDir, shortVersion);
      LOG.debug("Datei {} existiert: {}", methodTrace.getAbsolutePath(), methodTrace.exists());
   }
   
   private File getClazzDir(final String version, final TestCase testcase) {
      final File viewResultsFolder = new File(viewFolder, "view_" + version);
      if (!viewResultsFolder.exists()) {
         viewResultsFolder.mkdir();
      }
      String clazzDirName = (testcase.getModule() != null && !testcase.getModule().equals("")) ?
            testcase.getModule() + ChangedEntity.MODULE_SEPARATOR + testcase.getClazz() : testcase.getClazz();
      final File clazzDir = new File(viewResultsFolder, clazzDirName);
      if (!clazzDir.exists()) {
         clazzDir.mkdir();
      }
      return clazzDir;
   }
   
   private File writeTraces(final long sizeInMB, final TraceMethodReader traceMethodReader, final TraceWithMethods trace, List<File> traceFiles, final File methodDir,
         String shortVersion) throws IOException {
      final File currentTraceFile = new File(methodDir, shortVersion);
      traceFiles.add(currentTraceFile);
      Files.write(currentTraceFile.toPath(), trace.getWholeTrace().getBytes());
      final File commentlessTraceFile = new File(methodDir, shortVersion + OneTraceGenerator.NOCOMMENT);
      Files.write(commentlessTraceFile.toPath(), trace.getCommentlessTrace().getBytes());
      final File methodTrace = new File(methodDir, shortVersion + OneTraceGenerator.METHOD);
      Files.write(methodTrace.toPath(), trace.getTraceMethods().getBytes());
      if (sizeInMB < 5) {
         final File methodExpandedTrace = new File(methodDir, shortVersion + OneTraceGenerator.METHOD_EXPANDED);
         Files.write(methodExpandedTrace.toPath(), traceMethodReader.getExpandedTrace()
               .stream()
               .filter(value -> !(value instanceof RuleContent))
               .map(value -> value.toString()).collect(Collectors.toList()));
      } else {
         LOG.debug("Do not write expanded trace - size: {} MB", sizeInMB);
      }
      return methodTrace;
   }
}