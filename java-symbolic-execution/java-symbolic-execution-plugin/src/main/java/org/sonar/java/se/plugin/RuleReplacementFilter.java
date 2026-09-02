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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.api.rule.RuleKey;
import org.sonar.plugins.javasymbolicexecution.api.JavaRuleReplacements;

/**
 * Filters out symbolic execution rules that are replaced by rules provided by other plugins,
 * as declared through {@link JavaRuleReplacements}.
 */
final class RuleReplacementFilter {

  private final Set<RuleKey> replacedRuleKeys;

  RuleReplacementFilter(JavaRuleReplacements... replacements) {
    this.replacedRuleKeys = Arrays.stream(replacements)
      .flatMap(replacement -> replacement.replacedRuleKeys().stream())
      .collect(Collectors.toUnmodifiableSet());
  }

  boolean keeps(String ruleKey) {
    return !replacedRuleKeys.contains(RuleKey.of(JavaSECheckRegistrar.REPOSITORY_KEY, ruleKey));
  }
}
