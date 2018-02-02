package de.peran;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.MatchingAlgorithm.Matching;
import org.jgrapht.alg.matching.HopcroftKarpBipartiteMatching;
import org.jgrapht.alg.matching.HopcroftKarpMaximumCardinalityBipartiteMatching;
import org.jgrapht.alg.matching.MaximumWeightBipartiteMatching;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleGraph;
import org.jgrapht.graph.SimpleWeightedGraph;

import com.fasterxml.jackson.databind.ser.std.StdKeySerializers.Default;

import de.peran.dependency.analysis.PeASSFilter;
import de.peran.tracecomparison.ExecutionConsumer;
import de.peran.tracecomparison.ExecutionData;
import de.peran.tracecomparison.TraceCompareReadFilter;
import kieker.analysis.AnalysisController;
import kieker.analysis.exception.AnalysisConfigurationException;
import kieker.analysis.plugin.reader.filesystem.FSReader;
import kieker.common.configuration.Configuration;
import kieker.tools.traceAnalysis.filter.AbstractTraceAnalysisFilter;
import kieker.tools.traceAnalysis.filter.executionRecordTransformation.ExecutionRecordTransformationFilter;
import kieker.tools.traceAnalysis.filter.sessionReconstruction.SessionReconstructionFilter;
import kieker.tools.traceAnalysis.filter.traceReconstruction.TraceReconstructionFilter;
import kieker.tools.traceAnalysis.systemModel.Execution;
import kieker.tools.traceAnalysis.systemModel.repository.SystemModelRepository;

public class CompareTraces {

	private static final Logger LOG = LogManager.getLogger(CompareTraces.class);

	private final static Map<Integer, ExecutionData> compareExecutions = new HashMap<>();

	public static Map<Integer, Integer> createMatching(final List<Execution> first, final List<Execution> second) {
		final SimpleWeightedGraph<Execution, DefaultWeightedEdge> graph = initGraph(first, second);

		final Set<Execution> firstSet = new HashSet<>();
		firstSet.addAll(first);
		final Set<Execution> secondSet = new HashSet<>();
		secondSet.addAll(second);

		final MaximumWeightBipartiteMatching<Execution, DefaultWeightedEdge> matching = new MaximumWeightBipartiteMatching<Execution, DefaultWeightedEdge>(graph, firstSet, secondSet);
		final Matching<Execution, DefaultWeightedEdge> matchs = matching.getMatching();

		final Map<Integer, Integer> indexMatching = new HashMap<>();
		for (DefaultWeightedEdge edge : matchs.getEdges()) {
			Execution start = graph.getEdgeSource(edge);
			Execution end = graph.getEdgeTarget(edge);
			int startIndex = first.indexOf(start);
			int endIndex = second.indexOf(end);
			LOG.trace(startIndex + " " + endIndex + " " + start.getOperation().getSignature().getName() + " " + end.getOperation().getSignature().getName());
			indexMatching.put(startIndex, endIndex);
		}
		final Map<Integer, Integer> sortedMatching = new LinkedHashMap<>();
		indexMatching.keySet().stream().sorted().forEach(value -> sortedMatching.put(value, indexMatching.get(value)));

		return sortedMatching;
	}

	private static SimpleWeightedGraph<Execution, DefaultWeightedEdge> initGraph(List<Execution> first, List<Execution> second) {
		SimpleWeightedGraph<Execution, DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);

		for (Execution ex : first) {
			graph.addVertex(ex);
		}
		for (Execution ex : second) {
			graph.addVertex(ex);
		}

		for (Execution ex : first) {
			for (Execution ex2 : second) {
				String clazz1 = ex.getAllocationComponent().getAssemblyComponent().getType().getFullQualifiedName();
				String clazz2 = ex2.getAllocationComponent().getAssemblyComponent().getType().getFullQualifiedName();
				int basicValue = 100;
				if (clazz1.equals(clazz2)) {
					basicValue += 10000;
				}
				if (ex.getOperation().getSignature().equals(ex2.getOperation().getSignature())) {
					basicValue += 100000;
				}
				if (ex.getEss() != ex2.getEss()) {
					basicValue -= Math.abs(ex.getEss() - ex2.getEss()) * 10;
				}
				if (ex.getEoi() != ex2.getEoi()) {
					basicValue -= Math.abs(ex.getEss() - ex2.getEss()) * 10;
				}

				DefaultWeightedEdge edge = graph.addEdge(ex, ex2);
				graph.setEdgeWeight(edge, basicValue);
			}
		}
		return graph;
	}

	public static void main(String[] args) throws IllegalStateException, AnalysisConfigurationException, InterruptedException {
		final List<Thread> threads = new LinkedList<>();

		if (args.length == 1) {
			File folder = new File(args[0]);
			if (!folder.exists()) {
				LOG.error("Folder " + folder.getAbsolutePath() + " does not exist");
				System.exit(1);
			}

			File version1Folder = folder.listFiles()[0].listFiles()[0]; // only take 0th vm
			File version2Folder = folder.listFiles()[1].listFiles()[0];

			File trace1Folder = version1Folder.listFiles()[0].listFiles()[0].listFiles()[0];
			File trace2Folder = version2Folder.listFiles()[0].listFiles()[0].listFiles()[0];

			String testmethod = "org.apache.commons.io.IOUtilsCopyTestCase.testCopy_inputStreamToWriter_Encoding_nullEncoding";
			compareExecutions.put(0, new ExecutionData(testmethod));
			compareExecutions.put(1, new ExecutionData(testmethod));

			final CompareTraces cp = new CompareTraces(trace1Folder, compareExecutions.get(0));
			final CompareTraces cp2 = new CompareTraces(trace2Folder, compareExecutions.get(1));

			Thread t1 = new Thread(() -> {
				try {
					cp.start();
				} catch (IllegalStateException | AnalysisConfigurationException e) {
					e.printStackTrace();
				}
			});
			Thread t2 = new Thread(() -> {
				try {
					cp2.start();
				} catch (IllegalStateException | AnalysisConfigurationException e) {
					e.printStackTrace();
				}
			});

			threads.add(t1);
			threads.add(t2);

			t1.start();
			t2.start();
		}

		for (Thread thread : threads) {
			thread.join();
		}

		Map<Integer, Integer> matches = createMatching(compareExecutions.get(0).getFirstExecution(), compareExecutions.get(1).getFirstExecution());
		createComparison(matches);
	}

	private static void createComparison(Map<Integer, Integer> matches) {
		for (Map.Entry<Integer, Integer> match : matches.entrySet()) {
			LOG.debug("Match: " + match.getKey() + " " + match.getValue());
			ExecutionData first = compareExecutions.get(0);
			List<Execution> executions = first.getExecutions(match.getKey());
			Map<Integer, Execution> children = first.getChildren(match.getKey());
			ExecutionData second = compareExecutions.get(1);
			List<Execution> executions2 = second.getExecutions(match.getValue());
			ExecutionConsumer.compareExecutions(executions, executions2);
		}
	}

	private TraceReconstructionFilter traceReconstructionFilter;
	private final AnalysisController analysisController = new AnalysisController();
	private final File kiekerTraceFile;
	private final ExecutionData executions;

	public CompareTraces(File kiekerTraceFile, ExecutionData executions) {
		this.kiekerTraceFile = kiekerTraceFile;
		this.executions = executions;

	}

	private void initialiseTraceReading() throws AnalysisConfigurationException {
		// Initialize and register the list reader
		final Configuration fsReaderConfig = new Configuration();

		fsReaderConfig.setProperty(FSReader.CONFIG_PROPERTY_NAME_INPUTDIRS, kiekerTraceFile.getAbsolutePath());
		final FSReader reader = new FSReader(fsReaderConfig, analysisController);

		// Initialize and register the system model repository
		final SystemModelRepository systemModelRepository = new SystemModelRepository(new Configuration(), analysisController);

		final ExecutionRecordTransformationFilter executionRecordTransformationFilter = new ExecutionRecordTransformationFilter(new Configuration(),
				analysisController);

		analysisController.connect(executionRecordTransformationFilter,
				AbstractTraceAnalysisFilter.REPOSITORY_PORT_NAME_SYSTEM_MODEL, systemModelRepository);
		analysisController.connect(reader, FSReader.OUTPUT_PORT_NAME_RECORDS,
				executionRecordTransformationFilter, ExecutionRecordTransformationFilter.INPUT_PORT_NAME_RECORDS);

		traceReconstructionFilter = new TraceReconstructionFilter(new Configuration(), analysisController);
		analysisController.connect(traceReconstructionFilter,
				AbstractTraceAnalysisFilter.REPOSITORY_PORT_NAME_SYSTEM_MODEL, systemModelRepository);
		analysisController.connect(executionRecordTransformationFilter, ExecutionRecordTransformationFilter.OUTPUT_PORT_NAME_EXECUTIONS,
				traceReconstructionFilter, TraceReconstructionFilter.INPUT_PORT_NAME_EXECUTIONS);

		final Configuration bareSessionReconstructionFilterConfiguration = new Configuration();
		bareSessionReconstructionFilterConfiguration.setProperty(SessionReconstructionFilter.CONFIG_PROPERTY_NAME_MAX_THINK_TIME,
				SessionReconstructionFilter.CONFIG_PROPERTY_VALUE_MAX_THINK_TIME);
	}

	public void start() throws IllegalStateException, AnalysisConfigurationException {
		initialiseTraceReading();

		TraceCompareReadFilter filter = new TraceCompareReadFilter(new Configuration(), analysisController, executions);
		analysisController.connect(traceReconstructionFilter, TraceReconstructionFilter.OUTPUT_PORT_NAME_EXECUTION_TRACE,
				filter, PeASSFilter.INPUT_EXECUTION_TRACE);

		analysisController.run();
	}
}