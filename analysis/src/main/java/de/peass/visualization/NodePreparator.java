package de.peass.visualization;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.stat.descriptive.StatisticalSummary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.peass.measurement.analysis.Relation;
import de.peass.measurement.analysis.StatisticUtil;
import de.peass.measurement.rca.data.CallTreeNode;
import de.peass.measurement.rca.data.CauseSearchData;
import de.peass.measurement.rca.serialization.MeasuredNode;
import de.peass.measurement.rca.serialization.MeasuredValues;
import de.peass.measurement.rca.treeanalysis.TreeUtil;
import de.peass.visualization.GraphNode.State;

public class NodePreparator {

   private static final Logger LOG = LogManager.getLogger(NodePreparator.class);

   private CallTreeNode rootPredecessor, rootVersion;
   private final CauseSearchData data;
   final GraphNode root;

   public NodePreparator(final CallTreeNode rootPredecessor, final CallTreeNode rootVersion, final CauseSearchData data) {
      this.rootPredecessor = rootPredecessor;
      this.rootVersion = rootVersion;
      this.data = data;

      root = new GraphNode(data.getNodes().getCall(), data.getNodes().getKiekerPattern());
   }

   public NodePreparator(final CauseSearchData data) {
      this.rootPredecessor = null;
      this.rootVersion = null;
      this.data = data;

      root = new GraphNode(data.getNodes().getCall(), data.getNodes().getKiekerPattern());
   }

   public void prepare() {
      final MeasuredNode parent = data.getNodes();
      System.out.println(parent.getCall());
      setGraphData(parent, root);

      processNode(parent, root);

      if (rootPredecessor != null && rootVersion != null) {
         handleFullTreeNode(root, rootPredecessor, rootVersion);
      }

      preparePrefix(root);
   }

   private void handleFullTreeNode(final GraphNode graphNode, final CallTreeNode nodePredecessor, final CallTreeNode nodeVersion) {
      if (graphNode.getChildren().size() > 0) {
         handleMeasuredTriple(graphNode, nodePredecessor, nodeVersion);
      } else {
         handleUnmeasuredNode(graphNode, nodePredecessor, nodeVersion);
      }

   }

   private void handleUnmeasuredNode(final GraphNode graphNode, final CallTreeNode nodePredecessor, final CallTreeNode nodeVersion) {
      TreeUtil.findChildMapping(nodePredecessor, nodeVersion);

      final Set<String> addedEmptyChildren = new HashSet<>();
      for (final CallTreeNode purePredecessorChild : nodePredecessor.getChildren()) {
         boolean add = true;
         if (purePredecessorChild.getChildren().size() == 0) {
            if (addedEmptyChildren.contains(purePredecessorChild.getKiekerPattern())) {
               add = false;
            } else {
               addedEmptyChildren.add(purePredecessorChild.getKiekerPattern());
            }
         }
         if (add) {
            final GraphNode newChild = new GraphNode(purePredecessorChild.getCall(), purePredecessorChild.getKiekerPattern());
            newChild.setName(purePredecessorChild.getCall());
            newChild.setColor("#5555FF");
            newChild.setState(State.UNKNOWN);
            graphNode.getChildren().add(newChild);
            LOG.trace("Adding: " + purePredecessorChild.getCall() + " Parent: " + graphNode.getKiekerPattern());
            handleFullTreeNode(newChild, purePredecessorChild, purePredecessorChild.getOtherVersionNode());
         }

      }
   }

   private void handleMeasuredTriple(final GraphNode graphNode, final CallTreeNode nodePredecessor, final CallTreeNode nodeVersion) {
      TreeUtil.findChildMapping(nodePredecessor, nodeVersion);

      for (int index = 0; index < graphNode.getChildren().size(); index++) {
         final GraphNode graphChild = graphNode.getChildren().get(index);
         final CallTreeNode purePredecessorChild = nodePredecessor.getChildren().get(index);
         handleFullTreeNode(graphChild, purePredecessorChild, purePredecessorChild.getOtherVersionNode());
      }
   }

   private void preparePrefix(final GraphNode parent) {
      final String longestPrefix = getLongestPrefix(parent);
      setPrefix(parent, longestPrefix);
      LOG.info("Prefix: {}", longestPrefix);
   }

   private void setPrefix(final GraphNode parent, final String longestPrefix) {
      parent.setName(parent.getCall().substring(longestPrefix.length()));
      for (final GraphNode node : parent.getChildren()) {
         setPrefix(node, longestPrefix);
      }
   }

   private String getLongestPrefix(final GraphNode parent) {
      String longestPrefix = parent.getCall();
      for (final GraphNode node : parent.getChildren()) {
         final String clazz = node.getCall().substring(0, node.getCall().lastIndexOf('.') + 1);
         longestPrefix = StringUtils.getCommonPrefix(longestPrefix, getLongestPrefix(node), clazz);
      }
      return longestPrefix;
   }

   private void processNode(final MeasuredNode measuredParent, final GraphNode graphParent) {
      // final EqualChildDeterminer determiner = new EqualChildDeterminer();
      final Set<String> addedPatterns = new HashSet<>();
      for (final MeasuredNode measuredChild : measuredParent.getChilds()) {
         // if (!determiner.hasEqualSubtreeNode(measuredChild)) {
         final GraphNode newChild = createGraphNode(measuredChild);
         if (measuredChild.getCall().equals(CauseSearchData.ADDED) || measuredChild.getOtherKiekerPattern().equals(CauseSearchData.ADDED)) {
            newChild.setHasSourceChange(true);
         }

         newChild.setParent(measuredParent.getCall());
         setGraphData(measuredChild, newChild);

         graphParent.getChildren().add(newChild);
         processNode(measuredChild, newChild);
         addedPatterns.add(measuredChild.getKiekerPattern());
      }
      // }
   }

   private GraphNode createGraphNode(final MeasuredNode measuredChild) {
      final GraphNode newChild;
      if (measuredChild.getCall().equals(CauseSearchData.ADDED)) {
         final String pattern = measuredChild.getOtherKiekerPattern();
         String name = pattern.substring(pattern.lastIndexOf(' '), pattern.lastIndexOf('('));
         newChild = new GraphNode(name, pattern);
      } else {
         newChild = new GraphNode(measuredChild.getCall(), measuredChild.getKiekerPattern());
      }
      return newChild;
   }

   private void setGraphData(final MeasuredNode measuredChild, final GraphNode newChild) {
      newChild.setName(measuredChild.getCall());
      setNodeColor(measuredChild, newChild);
      if (measuredChild.getValues() != null) {
         final double[] values = getValueArray(measuredChild.getValues());
         newChild.setValues(values);
      }
      if (measuredChild.getValuesPredecessor() != null) {
         final double[] values = getValueArray(measuredChild.getValuesPredecessor());
         newChild.setValuesPredecessor(values);
      }
   }

   private double[] getValueArray(final MeasuredValues measured) {
      final double[] values = new double[measured.getValues().size()];
      for (int i = 0; i < values.length; i++) {
         final List<StatisticalSummary> statistics = measured.getValues().get(i);
         values[i] = StatisticUtil.getMean(statistics);
      }
      return values;
   }

   private void setNodeColor(final MeasuredNode measuredNode, final GraphNode graphNode) {
      graphNode.setStatistic(measuredNode.getStatistic());
      // final boolean isChange = StatisticUtil.agnosticTTest(measuredNode.getStatistic().getStatisticsOld(), measuredNode.getStatistic().getStatisticsCurrent(), data.getConfig())
      // == Relation.UNEQUAL;
      final StatisticalSummary statisticsOld = measuredNode.getStatistic().getStatisticsOld();
      final StatisticalSummary statisticsCurrent = measuredNode.getStatistic().getStatisticsCurrent();
      final boolean isChange = StatisticUtil.isChange(statisticsOld, statisticsCurrent, data.getMeasurementConfig()) == Relation.UNEQUAL;
      if (isChange && measuredNode.getStatistic().getMeanCurrent() > 0.001 && measuredNode.getStatistic().getMeanOld() > 0.001) {
         if (measuredNode.getStatistic().getTvalue() < 0) {
            graphNode.setColor("#FF0000");
            graphNode.setState(State.SLOWER);
            graphNode.getStatistic().setChange(true);
         } else {
            graphNode.setColor("#00FF00");
            graphNode.setState(State.FASTER);
            graphNode.getStatistic().setChange(true);
         }
      } else if (Double.isNaN(statisticsOld.getMean()) && !Double.isNaN(statisticsCurrent.getMean())) {
         graphNode.setColor("#00FF00");
         graphNode.setState(State.FASTER);
         graphNode.getStatistic().setChange(true);
      } else if (!Double.isNaN(statisticsOld.getMean()) && Double.isNaN(statisticsCurrent.getMean())) {
         graphNode.setColor("#FF0000");
         graphNode.setState(State.SLOWER);
         graphNode.getStatistic().setChange(true);
      } else {
         graphNode.setColor("#FFFFFF");
         graphNode.getStatistic().setChange(false);
      }
   }

   public GraphNode getRootNode() {
      return root;
   }
}
