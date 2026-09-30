/*
 * SonarQube Java
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * You can redistribute and/or modify this program under the terms of
 * the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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

import java.util.function.BiPredicate;
import org.sonar.java.se.constraint.Constraint;
import org.sonar.java.se.symbolicvalues.SymbolicValue;
import org.sonar.plugins.java.api.tree.ExpressionTree;

public class XxePropertyHolder {
  private final String propertyName;

  private final BiPredicate<SymbolicValue, ExpressionTree> securing;
  private final Constraint secured;

  private final BiPredicate<SymbolicValue, ExpressionTree> unsecuring;
  private final Constraint unsecured;

  private final Constraint named;

  public XxePropertyHolder(String propertyName, Constraint named,
    BiPredicate<SymbolicValue, ExpressionTree> securing, Constraint secured,
    BiPredicate<SymbolicValue, ExpressionTree> unsecuring, Constraint unsecured) {
    this.propertyName = propertyName;
    this.named = named;
    this.securing = securing;
    this.secured = secured;
    this.unsecuring = unsecuring;
    this.unsecured = unsecured;
  }

  String propertyName() {
    return propertyName;
  }

  BiPredicate<SymbolicValue, ExpressionTree> securing() {
    return securing;
  }

  Constraint secured() {
    return secured;
  }

  BiPredicate<SymbolicValue, ExpressionTree> unsecuring() {
    return unsecuring;
  }

  Constraint unsecured() {
    return unsecured;
  }

  Constraint named() {
    return named;
  }
}
