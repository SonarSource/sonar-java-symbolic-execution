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
package org.sonar.java.se.plugin;

import org.sonar.plugins.java.api.ProfileRegistrar;
import org.sonar.plugins.javasymbolicexecution.api.JavaRuleReplacements;

public class JavaSEProfileRegistrar implements ProfileRegistrar {

  private final RuleReplacementFilter replacementFilter;

  public JavaSEProfileRegistrar() {
    this(new JavaRuleReplacements[0]);
  }

  public JavaSEProfileRegistrar(JavaRuleReplacements[] replacements) {
    this.replacementFilter = new RuleReplacementFilter(replacements);
  }

  @Override
  public void register(RegistrarContext registrarContext) {
    registrarContext.registerDefaultQualityProfileRules(RulesList.getSonarWayRuleKeys(replacementFilter));
  }
}
