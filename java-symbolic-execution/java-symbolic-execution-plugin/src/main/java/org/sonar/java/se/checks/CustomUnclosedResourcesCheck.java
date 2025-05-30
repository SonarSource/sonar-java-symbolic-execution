/*
 * SonarQube Java
 * Copyright (C) 2012-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonar.java.se.checks;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;
import org.sonar.java.matcher.SEMethodMatcherFactory;
import org.sonar.java.se.CheckerContext;
import org.sonar.java.se.ExplodedGraph;
import org.sonar.java.se.Flow;
import org.sonar.java.se.FlowComputation;
import org.sonar.java.se.ProgramState;
import org.sonar.java.se.constraint.Constraint;
import org.sonar.java.se.constraint.ConstraintManager;
import org.sonar.java.se.symbolicvalues.SymbolicValue;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.semantic.MethodMatchers;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.NewClassTree;
import org.sonar.plugins.java.api.tree.ReturnStatementTree;
import org.sonar.plugins.java.api.tree.Tree;

@Rule(key = "S3546")
public class CustomUnclosedResourcesCheck extends SECheck {

  //see SONARJAVA-1624 class cannot be static, different classes are needed for every instance of this template rule
  public class CustomResourceConstraint implements Constraint {
    private final String valueAsString;

    CustomResourceConstraint(String valueAsString) {
      this.valueAsString = valueAsString;
    }

    @Override
    public String valueAsString() {
      return valueAsString;
    }
  }

  //see SONARJAVA-1624 fields cannot be static, different instances are needed for every instance of this template rule
  private final CustomResourceConstraint OPENED = new CustomResourceConstraint("open");
  private final CustomResourceConstraint CLOSED = new CustomResourceConstraint("close");

  @RuleProperty(
    key = "constructor",
    description = "the fully-qualified name of a constructor that creates an open resource."
      + " An optional signature may be specified after the class name."
      + " E.G. \"org.assoc.res.MyResource\" or \"org.assoc.res.MySpecialResource(java.lang.String, int)\"")
  public String constructor = "";

  @RuleProperty(
    key = "factoryMethod",
    description = "the fully-qualified name of a factory method that returns an open resource, with or without a parameter list."
      + " E.G. \"org.assoc.res.ResourceFactory$Innerclass#create\" or \"org.assoc.res.SpecialResourceFactory#create(java.lang.String, int)\"")
  public String factoryMethod = "";

  @RuleProperty(
    key = "openingMethod",
    description = "the fully-qualified name of a method that opens an existing resource, with or without a parameter list."
      + " E.G. \"org.assoc.res.ResourceFactory#create\" or \"org.assoc.res.SpecialResourceFactory #create(java.lang.String, int)\"")
  public String openingMethod = "";

  @RuleProperty(
    key = "closingMethod",
    description = "the fully-qualified name of the method which closes the open resource, with or without a parameter list."
      + " E.G. \"org.assoc.res.MyResource#closeMe\" or \"org.assoc.res.MySpecialResource#closeMe(java.lang.String, int)\"")
  public String closingMethod = "";

  private MethodMatchers classConstructor;

  private MethodMatchers factoryList;
  private MethodMatchers openingList;
  private MethodMatchers closingList;

  @Override
  public ProgramState checkPreStatement(CheckerContext context, Tree syntaxNode) {
    AbstractStatementVisitor visitor = new PreStatementVisitor(context);
    syntaxNode.accept(visitor);
    return visitor.programState;
  }

  @Override
  public ProgramState checkPostStatement(CheckerContext context, Tree syntaxNode) {
    PostStatementVisitor visitor = new PostStatementVisitor(context);
    syntaxNode.accept(visitor);
    return visitor.programState;
  }

  @Override
  public void checkEndOfExecutionPath(CheckerContext context, ConstraintManager constraintManager) {
    if (context.getState().exitingOnRuntimeException()) {
      return;
    }
    ExplodedGraph.Node node = context.getNode();
    context.getState().getValuesWithConstraints(OPENED).forEach(sv -> processUnclosedSymbolicValue(node, sv));
  }

  private void processUnclosedSymbolicValue(ExplodedGraph.Node node, SymbolicValue sv) {
    List<Class<? extends Constraint>> domains = Collections.singletonList(CustomResourceConstraint.class);
    FlowComputation.flowWithoutExceptions(node, sv, OPENED::equals, domains, FlowComputation.MAX_LOOKUP_FLOWS).stream()
      .flatMap(Flow::firstFlowLocation)
      .filter(location -> location.syntaxNode.is(Tree.Kind.NEW_CLASS, Tree.Kind.METHOD_INVOCATION))
      .forEach(this::reportIssue);
  }

  private void reportIssue(JavaFileScannerContext.Location location) {
    String message = "Close this \"" + name(location.syntaxNode) + "\".";
    reportIssue(location.syntaxNode, message);
  }

  private static String name(Tree tree) {
    if (tree.is(Tree.Kind.NEW_CLASS)) {
      return ((NewClassTree) tree).symbolType().name();
    }
    MethodInvocationTree mit = (MethodInvocationTree) tree;
    if (mit.symbolType().isVoid()) {
      return mit.methodSymbol().owner().name();
    }
    return mit.symbolType().name();
  }

  private static MethodMatchers createMethodMatchers(String rule) {
    if (rule.length() > 0) {
      return SEMethodMatcherFactory.methodMatchers(rule);
    } else {
      return MethodMatchers.none();
    }
  }

  private abstract class AbstractStatementVisitor extends CheckerTreeNodeVisitor {

    protected AbstractStatementVisitor(ProgramState programState) {
      super(programState);
    }

    protected void closeResource(@Nullable SymbolicValue target) {
      if (target != null) {
        CustomResourceConstraint oConstraint = programState.getConstraint(target, CustomResourceConstraint.class);
        if (oConstraint != null) {
          programState = programState.addConstraintTransitively(target.wrappedValue(), CLOSED);
        }
      }
    }

    protected void openResource(SymbolicValue sv) {
      programState = programState.addConstraintTransitively(sv, OPENED);
    }

    protected boolean isClosingResource(MethodInvocationTree mit) {
      return closingMethods().matches(mit);
    }

    private MethodMatchers closingMethods() {
      if (closingList == null) {
        closingList = createMethodMatchers(closingMethod);
      }
      return closingList;
    }
  }
  private class PreStatementVisitor extends AbstractStatementVisitor {

    protected PreStatementVisitor(CheckerContext context) {
      super(context.getState());
    }

    @Override
    public void visitNewClass(NewClassTree syntaxNode) {
      programState.peekValues(syntaxNode.arguments().size()).forEach(this::closeResource);
    }

    @Override
    public void visitMethodInvocation(MethodInvocationTree mit) {
      if (isOpeningResource(mit)) {
        openResource(getTargetSV(mit));
      } else if (isClosingResource(mit)) {
        closeResource(getTargetSV(mit));
      } else {
        programState.peekValues(mit.arguments().size()).forEach(this::closeResource);
      }
    }

    private SymbolicValue getTargetSV(MethodInvocationTree mit) {
      return programState.peekValue(mit.arguments().size());
    }

    private boolean isOpeningResource(MethodInvocationTree syntaxNode) {
      return openingMethods().matches(syntaxNode);
    }

    @Override
    public void visitReturnStatement(ReturnStatementTree syntaxNode) {
      ExpressionTree expression = syntaxNode.expression();
      if (expression != null) {
        closeResource(programState.peekValue());
      }
    }

    private MethodMatchers openingMethods() {
      if (openingList == null) {
        openingList = createMethodMatchers(openingMethod);
      }
      return openingList;
    }
  }
  private class PostStatementVisitor extends AbstractStatementVisitor {

    protected PostStatementVisitor(CheckerContext context) {
      super(context.getState());
    }

    @Override
    public void visitNewClass(NewClassTree newClassTree) {
      if (isCreatingResource(newClassTree)) {
        openResource(programState.peekValue());
      }
    }

    @Override
    public void visitMethodInvocation(MethodInvocationTree mit) {
      if (isCreatingResource(mit)) {
        openResource(programState.peekValue());
      }
    }

    private boolean isCreatingResource(NewClassTree newClassTree) {
      return constructorClasses().matches(newClassTree);
    }

    private MethodMatchers constructorClasses() {
      if (classConstructor == null) {
        classConstructor = MethodMatchers.none();
        if (constructor.length() > 0) {
          classConstructor = SEMethodMatcherFactory.constructorMatcher(constructor);
        }
      }
      return classConstructor;
    }

    private boolean isCreatingResource(MethodInvocationTree mit) {
      return factoryMethods().matches(mit);
    }

    private MethodMatchers factoryMethods() {
      if (factoryList == null) {
        factoryList = createMethodMatchers(factoryMethod);
      }
      return factoryList;
    }

  }

}
