package de.peran.dependency.traces;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;

import de.peran.dependency.analysis.CalledMethodLoader;
import de.peran.dependency.analysis.data.TraceElement;
import de.peran.dependency.traces.requitur.ReducedTraceElement;
import de.peran.dependency.traces.requitur.RunLengthEncodingSequitur;
import de.peran.dependency.traces.requitur.Sequitur;
import de.peran.dependency.traces.requitur.content.TraceElementContent;

/**
 * Reads the traces of kieker results and combines them with source information read by javaparser.
 * 
 * @author reichelt
 *
 */
public class TraceMethodReader {

	public static final String CYCLE_PREFIX = "#Generated$";

	private static final Logger LOG = LogManager.getLogger(TraceMethodReader.class);

	final List<TraceElement> calls;
	private final File[] clazzFolder;

	private final int LOOKBACK_LENGTH = 15;

	final Map<File, CompilationUnit> loadedUnits = new HashMap<>();

	public TraceMethodReader(final List<TraceElement> calls, final File... clazzFolder) {
		this.calls = calls;
		this.clazzFolder = clazzFolder;
	}

	public TraceMethodReader(final File traceFolder, final File... clazzFolder) {
		this.calls = new CalledMethodLoader(traceFolder).getShortTrace(null);
		this.clazzFolder = clazzFolder;
	}

	public TraceWithMethods getTraceWithMethods() throws ParseException, IOException {
		final Sequitur seq = new Sequitur();
		seq.addTraceElements(calls);
		final RunLengthEncodingSequitur runLengthEncodingSequitur = new RunLengthEncodingSequitur(seq);
		runLengthEncodingSequitur.reduce();
		final List<ReducedTraceElement> rleTrace = runLengthEncodingSequitur.getReadableRLETrace();
		final TraceWithMethods trace = new TraceWithMethods(rleTrace);
		for (final ReducedTraceElement traceElement : rleTrace) {
			if (traceElement.getValue() instanceof TraceElementContent) {
				final TraceElementContent te = (TraceElementContent) traceElement.getValue();
				final File clazzFile = TraceReadUtils.getClazzFile(te, clazzFolder);
				if (clazzFile != null) {
					CompilationUnit cu = loadedUnits.get(clazzFile);
					if (cu == null) {
						LOG.info("CU " + clazzFile + " not imported yet");
						cu = JavaParser.parse(clazzFile);
						loadedUnits.put(clazzFile, cu);
					}
					final Node method = TraceReadUtils.getMethod(te, cu);
					trace.setElementSource(te, method != null ? method.toString().intern() : null);
				}
			}
		}
		

		return trace;
	}

}