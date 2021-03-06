package de.dagere.peass;

import picocli.CommandLine.Option;

public class KiekerConfigMixin {
   @Option(names = { "-writeInterval", "--writeInterval" }, description = "Interval for KoPeMe-aggregated-writing (in milliseconds)")
   public int writeInterval = 5000;

   @Option(names = { "-notUseSourceInstrumentation", "--notUseSourceInstrumentation" }, description = "Not use source instrumentation (disabling enables AspectJ instrumentation)")
   public boolean notUseSourceInstrumentation = false;

   @Option(names = { "-useCircularQueue", "--useCircularQueue" }, description = "Use circular queue (default false - LinkedBlockingQueue is used)")
   public boolean useCircularQueue = false;

   @Option(names = { "-notUseSelectiveInstrumentation",
         "--notUseSelectiveInstrumentation" }, description = "Use selective instrumentation (only selected methods / classes are instrumented) - is activated by default is source instrumentation is activated")
   public boolean notUseSelectiveInstrumentation = false;

   @Option(names = { "-useSampling",
         "--useSampling" }, description = "Use sampling (only record every nth invocation of method - may reduce measurement noise)")
   public boolean useSampling = false;

   public int getWriteInterval() {
      return writeInterval;
   }

   public boolean isNotUseSourceInstrumentation() {
      return notUseSourceInstrumentation;
   }

   public boolean isUseCircularQueue() {
      return useCircularQueue;
   }

   public boolean isNotUseSelectiveInstrumentation() {
      return notUseSelectiveInstrumentation;
   }

   public boolean isUseSampling() {
      return useSampling;
   }
   
   
}
