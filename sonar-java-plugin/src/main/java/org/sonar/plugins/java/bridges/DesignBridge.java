/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.java.bridges;

import com.google.common.base.Predicate;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.design.Dependency;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.Measure;
import org.sonar.api.measures.Metric;
import org.sonar.api.measures.PersistenceMode;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.api.rules.ActiveRule;
import org.sonar.api.rules.Violation;
import org.sonar.api.utils.TimeProfiler;
import org.sonar.graph.Cycle;
import org.sonar.graph.Dsm;
import org.sonar.graph.DsmTopologicalSorter;
import org.sonar.graph.Edge;
import org.sonar.graph.IncrementalCyclesAndFESSolver;
import org.sonar.graph.MinimumFeedbackEdgeSetSolver;
import org.sonar.java.JavaSquid;
import org.sonar.java.ast.visitors.PackageVisitor;
import org.sonar.java.checks.CycleBetweenPackagesCheck;
import org.sonar.squidbridge.api.SourceCode;
import org.sonar.squidbridge.api.SourceCodeEdge;
import org.sonar.squidbridge.api.SourcePackage;
import org.sonar.squidbridge.api.SourceProject;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class DesignBridge extends Bridge {

  private static final Logger LOG = LoggerFactory.getLogger(DesignBridge.class);

  /*
   * This index is shared between onProject() and onPackage(). It works because onProject() is executed before onPackage().
   */
  private DependencyIndex dependencyIndex = new DependencyIndex();

  @Override
  public boolean needsBytecode() {
    return true;
  }

  @Override
  public void onProject(SourceProject squidProject, Project sonarProject) {
    Set<SourceCode> squidPackages = squidProject.getChildren();
    if (squidPackages != null) {
      squidPackages = Sets.filter(squidPackages, new Predicate<SourceCode>() {
        @Override
        public boolean apply(@Nullable SourceCode input) {
          return input != null && !PackageVisitor.UNRESOLVED_PACKAGE.equals(input.getKey());
        }
      });
      if (squidPackages.isEmpty()) {
        return;
      }
      TimeProfiler profiler = new TimeProfiler(LOG).start("Package design analysis");
      LOG.debug("{} packages to analyze", squidPackages.size());

      savePackageDependencies(squidPackages);

      IncrementalCyclesAndFESSolver<SourceCode> cyclesAndFESSolver = new IncrementalCyclesAndFESSolver<SourceCode>(squid, squidPackages);
      LOG.debug("{} cycles", cyclesAndFESSolver.getCycles().size());

      Set<Edge> feedbackEdges = cyclesAndFESSolver.getFeedbackEdgeSet();
      LOG.debug("{} feedback edges", feedbackEdges.size());
      int tangles = cyclesAndFESSolver.getWeightOfFeedbackEdgeSet();

      saveViolations(feedbackEdges);
      savePositiveMeasure(sonarProject, CoreMetrics.PACKAGE_CYCLES, cyclesAndFESSolver.getCycles().size());
      savePositiveMeasure(sonarProject, CoreMetrics.PACKAGE_FEEDBACK_EDGES, feedbackEdges.size());
      savePositiveMeasure(sonarProject, CoreMetrics.PACKAGE_TANGLES, tangles);
      savePositiveMeasure(sonarProject, CoreMetrics.PACKAGE_EDGES_WEIGHT, getEdgesWeight(squidPackages));

      String dsmJson = serializeDsm(squid, squidPackages, feedbackEdges);
      Measure dsmMeasure = new Measure(CoreMetrics.DEPENDENCY_MATRIX, dsmJson).setPersistenceMode(PersistenceMode.DATABASE);
      context.saveMeasure(sonarProject, dsmMeasure);

      profiler.stop();
    }
  }

  private void savePositiveMeasure(Resource sonarResource, Metric metric, double value) {
    if (value >= 0.0) {
      context.saveMeasure(sonarResource, metric, value);
    }
  }

  @Override
  public void onPackage(SourcePackage squidPackage, Resource sonarPackage) {
    Set<SourceCode> squidFiles = squidPackage.getChildren();
    if (squidFiles != null && !squidFiles.isEmpty()) {

      saveFileDependencies(squidFiles);

      IncrementalCyclesAndFESSolver<SourceCode> cycleDetector = new IncrementalCyclesAndFESSolver<SourceCode>(squid, squidFiles);
      Set<Cycle> cycles = cycleDetector.getCycles();

      MinimumFeedbackEdgeSetSolver solver = new MinimumFeedbackEdgeSetSolver(cycles);
      Set<Edge> feedbackEdges = solver.getEdges();
      int tangles = solver.getWeightOfFeedbackEdgeSet();

      savePositiveMeasure(sonarPackage, CoreMetrics.FILE_CYCLES, cycles.size());
      savePositiveMeasure(sonarPackage, CoreMetrics.FILE_FEEDBACK_EDGES, feedbackEdges.size());
      savePositiveMeasure(sonarPackage, CoreMetrics.FILE_TANGLES, tangles);
      savePositiveMeasure(sonarPackage, CoreMetrics.FILE_EDGES_WEIGHT, getEdgesWeight(squidFiles));

      String dsmJson = serializeDsm(squid, squidFiles, feedbackEdges);
      context.saveMeasure(sonarPackage, new Measure(CoreMetrics.DEPENDENCY_MATRIX, dsmJson));
    }
  }

  private double getEdgesWeight(Collection<SourceCode> sourceCodes) {
    List<SourceCodeEdge> edges = squid.getEdges(sourceCodes);
    double total = 0.0;
    for (SourceCodeEdge edge : edges) {
      total += edge.getWeight();
    }
    return total;
  }

  private String serializeDsm(JavaSquid squid, Set<SourceCode> squidSources, Set<Edge> feedbackEdges) {
    Dsm<SourceCode> dsm = new Dsm<SourceCode>(squid, squidSources, feedbackEdges);
    DsmTopologicalSorter.sort(dsm);
    return DsmSerializer.serialize(dsm, dependencyIndex, resourceIndex);
  }

  /**
   * Save package dependencies, including root file dependencies
   */
  public void savePackageDependencies(Set<SourceCode> squidPackages) {
    for (SourceCode squidPackage : squidPackages) {
      for (SourceCodeEdge edge : squid.getOutgoingEdges(squidPackage)) {
        Dependency dependency = saveEdge(edge, context, null);
        if (dependency != null) {
          // save file dependencies
          for (SourceCodeEdge subEdge : edge.getRootEdges()) {
            saveEdge(subEdge, context, dependency);
          }
        }
      }
    }
  }

  private void saveViolations(Set<Edge> feedbackEdges) {
    ActiveRule rule = CycleBetweenPackagesCheck.getActiveRule(checkFactory);
    if (rule == null) {
      // Rule inactive
      return;
    }
    for (Edge feedbackEdge : feedbackEdges) {
      SourceCode fromPackage = (SourcePackage) feedbackEdge.getFrom();
      SourceCode toPackage = (SourcePackage) feedbackEdge.getTo();
      SourceCodeEdge edge = squid.getEdge(fromPackage, toPackage);
      for (SourceCodeEdge subEdge : edge.getRootEdges()) {
        Resource fromFile = resourceIndex.get(subEdge.getFrom());
        Resource toFile = resourceIndex.get(subEdge.getTo());
        // If resource cannot be obtained, then silently ignore, because anyway warning will be printed by method saveEdge
        if ((fromFile != null) && (toFile != null)) {
          Violation violation = Violation.create(rule, fromFile)
              .setMessage("Remove the dependency on the source file \"" + toFile.getLongName() + "\" to break a package cycle.")
              .setCost((double) subEdge.getWeight());
          context.saveViolation(violation);
        }
      }
    }
  }

  /**
   * Save file dependencies
   */
  public void saveFileDependencies(Set<SourceCode> squidFiles) {
    for (SourceCode squidFile : squidFiles) {
      for (SourceCodeEdge edge : squid.getOutgoingEdges(squidFile)) {
        saveEdge(edge, context, null);
      }
    }
  }

  private Dependency saveEdge(SourceCodeEdge edge, SensorContext context, Dependency parentDependency) {
    Dependency dependency = dependencyIndex.get(edge);
    if (dependency == null) {
      Resource from = resourceIndex.get(edge.getFrom());
      Resource to = resourceIndex.get(edge.getTo());
      if (from != null && to != null) {
        dependency = new Dependency(from, to).setUsage(edge.getUsage().name()).setWeight(edge.getWeight()).setParent(parentDependency);
        context.saveDependency(dependency);
        dependencyIndex.put(edge, dependency);
      } else {
        if (from == null) {
          LOG.warn("Unable to find resource '" + edge.getFrom() + "' to create a dependency with '" + edge.getTo() + "'");
        }
        if (to == null) {
          LOG.warn("Unable to find resource '" + edge.getTo() + "' to create a dependency with '" + edge.getFrom() + "'");
        }
        return null;
      }
    }
    return dependency;
  }

}
