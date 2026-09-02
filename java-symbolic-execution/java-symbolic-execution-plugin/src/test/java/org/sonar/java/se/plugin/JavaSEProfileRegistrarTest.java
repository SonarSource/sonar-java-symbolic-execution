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
import org.sonar.java.checks.verifier.TestProfileRegistrarContext;
import org.sonar.plugins.javasymbolicexecution.api.JavaRuleReplacements;

import static org.assertj.core.api.Assertions.assertThat;

class JavaSEProfileRegistrarTest {

  @Test
  void constructor() {
    JavaSEProfileRegistrar registrar = new JavaSEProfileRegistrar();
    TestProfileRegistrarContext context = new TestProfileRegistrarContext();
    registrar.register(context);
    assertThat(context.rulesByQualityProfile.get("Sonar way")).hasSize(21);
  }

  @Test
  void replaced_rules_are_not_added_to_profile() {
    JavaRuleReplacements first = () -> Set.of(RuleKey.of("java", "S2259"));
    JavaRuleReplacements second = () -> Set.of(RuleKey.of("java", "S3518"), RuleKey.of("other", "S2095"));
    JavaSEProfileRegistrar registrar = new JavaSEProfileRegistrar(new JavaRuleReplacements[] {first, second});
    TestProfileRegistrarContext context = new TestProfileRegistrarContext();

    registrar.register(context);

    assertThat(context.rulesByQualityProfile.get("Sonar way"))
      .extracting(RuleKey::toString)
      .doesNotContain("java:S2259", "java:S3518")
      .contains("java:S2095")
      .hasSize(19);
  }

  @Test
  void non_replaced_rules_are_kept_in_profile() {
    JavaSEProfileRegistrar registrar = new JavaSEProfileRegistrar();
    TestProfileRegistrarContext context = new TestProfileRegistrarContext();

    registrar.register(context);

    assertThat(context.rulesByQualityProfile.get("Sonar way"))
      .extracting(RuleKey::toString)
      .contains("java:S2259", "java:S3518");
  }

}
