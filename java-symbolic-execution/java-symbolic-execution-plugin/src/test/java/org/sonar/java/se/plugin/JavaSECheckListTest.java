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

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sonar.api.rule.RuleKey;
import org.sonar.java.se.checks.NullDereferenceCheck;
import org.sonar.plugins.javasymbolicexecution.api.JavaRuleReplacements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class JavaSECheckListTest {

  @Test
  void getChecks() {
    assertThat(JavaSECheckList.getChecks(new RuleReplacementFilter())).isNotNull().hasSize(23);
  }

  @Test
  void getChecks_excludes_replaced_rules() {
    JavaRuleReplacements replacements = () -> Set.of(RuleKey.of("java", "S2259"));

    assertThat(JavaSECheckList.getChecks(new RuleReplacementFilter(replacements)))
      .doesNotContain(NullDereferenceCheck.class)
      .hasSize(22);
  }

  @Test
  void getChecks_rejects_replacement_of_rule_with_dependent_checks() {
    JavaRuleReplacements replacements = () -> Set.of(RuleKey.of("java", "S2755"));

    assertThatIllegalArgumentException()
      .isThrownBy(() -> JavaSECheckList.getChecks(new RuleReplacementFilter(replacements)))
      .withMessage("Rule java:S2755 cannot be replaced because other symbolic execution rules depend on it");
  }

}
