package de.peass.kiekerInstrument;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.TryStmt;

import de.peass.dependency.execution.AllowedKiekerRecord;

public class BlockBuilder {

   public static BlockStmt buildStatement(BlockStmt originalBlock, String signature, boolean addReturn, AllowedKiekerRecord recordType) {
      if (recordType.equals(AllowedKiekerRecord.OPERATIONEXECUTION)) {
         return buildOperationExecutionStatement(originalBlock, signature, addReturn);
      } else if (recordType.equals(AllowedKiekerRecord.REDUCED_OPERATIONEXECUTION)) {
         return buildReducedOperationExecutionStatement(originalBlock, signature, addReturn);
      } else {
         throw new RuntimeException();
      }
   }

   public static BlockStmt buildReducedOperationExecutionStatement(BlockStmt originalBlock, String signature, boolean addReturn) {
      BlockStmt replacedStatement = new BlockStmt();

      buildHeader(originalBlock, signature, addReturn, replacedStatement);
      replacedStatement.addAndGetStatement("      final long tin = MonitoringController.getInstance().getTimeSource().getTime();");

      BlockStmt finallyBlock = new BlockStmt();
      finallyBlock.addAndGetStatement("// measure after\n" +
            "         final long tout = MonitoringController.getInstance().getTimeSource().getTime();\n" +
            "         MonitoringController.getInstance().newMonitoringRecord(new ReducedOperationExecutionRecord(signature, tin, tout));");
      TryStmt stmt = new TryStmt(originalBlock, new NodeList<>(), finallyBlock);
      replacedStatement.addAndGetStatement(stmt);
      return replacedStatement;
   }

   public static BlockStmt buildOperationExecutionStatement(BlockStmt originalBlock, String signature, boolean addReturn) {
      BlockStmt replacedStatement = new BlockStmt();

      buildHeader(originalBlock, signature, addReturn, replacedStatement);
      replacedStatement.addAndGetStatement("      // collect data\n" +
            "      final boolean entrypoint;\n" +
            "      final String hostname = MonitoringController.getInstance().getHostname();\n" +
            "      final String sessionId = SessionRegistry.INSTANCE.recallThreadLocalSessionId();\n" +
            "      final int eoi; // this is executionOrderIndex-th execution in this trace\n" +
            "      final int ess; // this is the height in the dynamic call tree of this execution\n" +
            "      long traceId = ControlFlowRegistry.INSTANCE.recallThreadLocalTraceId(); // traceId, -1 if entry point\n" +
            "      if (traceId == -1) {\n" +
            "         entrypoint = true;\n" +
            "         traceId = ControlFlowRegistry.INSTANCE.getAndStoreUniqueThreadLocalTraceId();\n" +
            "         ControlFlowRegistry.INSTANCE.storeThreadLocalEOI(0);\n" +
            "         ControlFlowRegistry.INSTANCE.storeThreadLocalESS(1); // next operation is ess + 1\n" +
            "         eoi = 0;\n" +
            "         ess = 0;\n" +
            "      } else {\n" +
            "         entrypoint = false;\n" +
            "         eoi = ControlFlowRegistry.INSTANCE.incrementAndRecallThreadLocalEOI(); // ess > 1\n" +
            "         ess = ControlFlowRegistry.INSTANCE.recallAndIncrementThreadLocalESS(); // ess >= 0\n" +
            "         if ((eoi == -1) || (ess == -1)) {\n" +
            "            System.err.println(\"eoi and/or ess have invalid values: eoi == {} ess == {}\"+ eoi+ \"\" + ess);\n" +
            "            MonitoringController.getInstance().terminateMonitoring();\n" +
            "         }\n" +
            "      }\n" +
            "      // measure before\n" +
            "      final long tin = MonitoringController.getInstance().getTimeSource().getTime();");
      BlockStmt finallyBlock = new BlockStmt();
      finallyBlock.addAndGetStatement("// measure after\n" +
            "         final long tout = MonitoringController.getInstance().getTimeSource().getTime();\n" +
            "         MonitoringController.getInstance().newMonitoringRecord(new OperationExecutionRecord(signature, sessionId, traceId, tin, tout, hostname, eoi, ess));\n" +
            "         // cleanup\n" +
            "         if (entrypoint) {\n" +
            "            ControlFlowRegistry.INSTANCE.unsetThreadLocalTraceId();\n" +
            "            ControlFlowRegistry.INSTANCE.unsetThreadLocalEOI();\n" +
            "            ControlFlowRegistry.INSTANCE.unsetThreadLocalESS();\n" +
            "         } else {\n" +
            "            ControlFlowRegistry.INSTANCE.storeThreadLocalESS(ess); // next operation is ess\n" +
            "         }");
      TryStmt stmt = new TryStmt(originalBlock, new NodeList<>(), finallyBlock);
      replacedStatement.addAndGetStatement(stmt);
      return replacedStatement;
   }

   private static void buildHeader(BlockStmt originalBlock, String signature, boolean addReturn, BlockStmt replacedStatement) {
      replacedStatement.addAndGetStatement("if (!MonitoringController.getInstance().isMonitoringEnabled()) {\n" +
            originalBlock.toString() +
            (addReturn ? "return;" : "") +
            "      }");
      replacedStatement.addAndGetStatement("final String signature = \"" + signature + "\";");
      replacedStatement.addAndGetStatement("if (!MonitoringController.getInstance().isProbeActivated(signature)) {\n" +
            originalBlock.toString() +
            (addReturn ? "return;" : "") +
            "      }");
   }
}
