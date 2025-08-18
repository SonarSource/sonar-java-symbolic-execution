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
package org.sonar.java.model;

import java.util.Optional;
import org.sonar.plugins.java.api.tree.AssignmentExpressionTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.ParenthesizedTree;
import org.sonar.plugins.java.api.tree.Tree;

public final class SEExpressionUtils {

  private SEExpressionUtils() {
  }

  /**
   * Checks of is the given tree is selecting with <code>this</code> or <code>super</code>
   * @param tree The tree to check.
   * @return true when the tree is a select on <code>this</code> or <code>super</code>
   * @see #isSelectOnThisOrSuper(AssignmentExpressionTree)
   */
  public static boolean isSelectOnThisOrSuper(MemberSelectExpressionTree tree) {
    if (!tree.expression().is(Tree.Kind.IDENTIFIER)) {
      // This is no longer simple.
      return false;
    }

    String selectSourceName = ((IdentifierTree) tree.expression()).name();
    return "this".equalsIgnoreCase(selectSourceName) || "super".equalsIgnoreCase(selectSourceName);
  }

  /**
   * Checks of is the given tree is a {@link MemberSelectExpressionTree} which is selecting with <code>this</code> or <code>super</code>
   * @param tree The tree to check.
   * @return true when the tree is a select on <code>this</code> or <code>super</code>
   * @see #isSelectOnThisOrSuper(MemberSelectExpressionTree)
   */
  public static boolean isSelectOnThisOrSuper(AssignmentExpressionTree tree) {
    ExpressionTree variable = SEExpressionUtils.skipParentheses(tree.variable());
    return variable.is(Tree.Kind.MEMBER_SELECT) && isSelectOnThisOrSuper((MemberSelectExpressionTree) variable);
  }

  public static ExpressionTree skipParentheses(ExpressionTree tree) {
    ExpressionTree result = tree;
    while (result.is(Tree.Kind.PARENTHESIZED_EXPRESSION)) {
      result = ((ParenthesizedTree) result).expression();
    }
    return result;
  }

  private static Optional<IdentifierTree> extractIdentifier(ExpressionTree tree) {
    ExpressionTree cleanedExpression = SEExpressionUtils.skipParentheses(tree);
    if (cleanedExpression.is(Tree.Kind.IDENTIFIER)) {
      return Optional.of(((IdentifierTree) cleanedExpression));
    } else if (cleanedExpression.is(Tree.Kind.MEMBER_SELECT)) {
      MemberSelectExpressionTree selectTree = (MemberSelectExpressionTree) cleanedExpression;
      if (isSelectOnThisOrSuper(selectTree)) {
        return Optional.of(selectTree.identifier());
      }
    }
    return Optional.empty();
  }

  /**
   * In case of simple assignments, only the expression is evaluated, as we only use the reference to the variable to store the result.
   * For SE-Based checks, only a single value should be unstacked if its the case. For other cases, two values should be unstacked.
   * See JLS8-15.26
   *
   * @param tree The assignment tree
   * @return true if the tree is a simple assignment
   * @see #extractIdentifier(AssignmentExpressionTree)
   */
  public static boolean isSimpleAssignment(AssignmentExpressionTree tree) {
    if (!tree.is(Tree.Kind.ASSIGNMENT)) {
      // This can't possibly be a simple assignment.
      return false;
    }

    ExpressionTree variable = SEExpressionUtils.skipParentheses(tree.variable());
    return variable.is(Tree.Kind.IDENTIFIER) || isSelectOnThisOrSuper(tree);
  }

  /**
   * Retrieve the identifier corresponding to the method name associated to the method invocation
   */
  public static IdentifierTree methodName(MethodInvocationTree mit) {
    ExpressionTree methodSelect = mit.methodSelect();
    IdentifierTree id;
    if (methodSelect.is(Tree.Kind.IDENTIFIER)) {
      id = (IdentifierTree) methodSelect;
    } else {
      id = ((MemberSelectExpressionTree) methodSelect).identifier();
    }
    return id;
  }

  public static boolean isNullLiteral(ExpressionTree tree) {
    return skipParentheses(tree).is(Tree.Kind.NULL_LITERAL);
  }

  public static IdentifierTree extractIdentifier(AssignmentExpressionTree tree) {
    Optional<IdentifierTree> identifier = extractIdentifier(tree.variable());

    if (identifier.isPresent()) {
      return identifier.get();
    }

    // This should not be possible.
    // If it happens anyway, you should make sure the assignment is simple (by calling isSimpleAssignment) before.
    throw new IllegalArgumentException("Can not extract identifier.");
  }

  /**
   * Checks if the given expression refers to "this"
   * @param expression the expression to check
   * @return true if this expression refers to "this"
   */
  public static boolean isThis(ExpressionTree expression) {
    ExpressionTree newExpression = SEExpressionUtils.skipParentheses(expression);
    return newExpression.is(Tree.Kind.IDENTIFIER) && "this".equals(((IdentifierTree) newExpression).name());
  }

}
