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
package org.sonar.java.se.plugin;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonar.api.SonarEdition;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.SonarRuntime;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.rule.internal.NewActiveRule;
import org.sonar.api.internal.SonarRuntimeImpl;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.utils.Version;
import org.sonar.check.Rule;
import org.sonar.java.checks.verifier.TestCheckRegistrarContext;
import org.sonar.java.se.checks.SECheck;
import org.sonar.plugins.java.api.CheckRegistrar;

import static org.assertj.core.api.Assertions.assertThat;

class JavaSECheckRegistrarTest {

  private static final ActiveRules activeRules = activeRules(getRuleKeysWithRepo());

  private static final List<String> rulesNotActiveByDefault = List.of(
    "S2259",
    "S2583",
    "S2589",
    "S3518",
    "S3546",
    "S3655",
    "S3959",
    "S6374"
  );

  private static final SonarRuntime SQC = SonarRuntimeImpl.forSonarQube(
    Version.parse("8.1"),
    SonarQubeSide.SERVER,
    SonarEdition.SONARCLOUD
  );
  private static final SonarRuntime SQCB = SonarRuntimeImpl.forSonarQube(
    Version.parse("25.1"),
    SonarQubeSide.SERVER,
    SonarEdition.COMMUNITY
  );
  private static final SonarRuntime SQS_DEVELOPER = SonarRuntimeImpl.forSonarQube(
    Version.parse("2025.1"),
    SonarQubeSide.SERVER,
    SonarEdition.DEVELOPER
  );
  private static final SonarRuntime SQ_FOR_IDE = SonarRuntimeImpl.forSonarLint(Version.parse("10.22.0.81232"));

  @Test
  void register_rules() {
    SonarRuntime sonarqubeServerDeveloper = SonarRuntimeImpl.forSonarQube(
      Version.parse("2025.1"),
      SonarQubeSide.SERVER,
      SonarEdition.DEVELOPER
    );
    CheckRegistrar registrar = new JavaSECheckRegistrar(sonarqubeServerDeveloper);
    TestCheckRegistrarContext context = new TestCheckRegistrarContext();

    CheckFactory checkFactory = new CheckFactory(activeRules);
    registrar.register(context, checkFactory);

    assertThat(context.mainRuleKeys).map(RuleKey::toString).containsExactlyInAnyOrder(getRuleKeysWithRepo());
    assertThat(context.testRuleKeys).isEmpty();
  }

  @Test
  void getChecks_returns_the_expected_amount_of_checks_depending_on_the_runtime() {
    assertThat(JavaSECheckRegistrar.getChecks(SQC)).hasSize(22);
    assertThat(JavaSECheckRegistrar.getChecks(SQS_DEVELOPER)).hasSize(22);
    assertThat(JavaSECheckRegistrar.getChecks(SQ_FOR_IDE)).hasSize(22);
    assertThat(JavaSECheckRegistrar.getChecks(SQCB)).hasSize(23);
  }

  @Test
  void is_only_in_standalone_mode_in_sqcb() {
    assertThat(JavaSECheckRegistrar.isStandaloneSymbolicExecutionAnalyzer(SQCB)).isTrue();

    assertThat(JavaSECheckRegistrar.isStandaloneSymbolicExecutionAnalyzer(SQS_DEVELOPER)).isFalse();
    assertThat(JavaSECheckRegistrar.isStandaloneSymbolicExecutionAnalyzer(SQC)).isFalse();

    assertThat(JavaSECheckRegistrar.isStandaloneSymbolicExecutionAnalyzer(SQ_FOR_IDE)).isFalse();
  }


  @Test
  void rules_definition() {
    SonarRuntime sonarRuntime = SonarRuntimeImpl.forSonarQube(Version.create(10, 2), SonarQubeSide.SERVER, SonarEdition.ENTERPRISE);
    JavaSECheckRegistrar rulesDefinition = new JavaSECheckRegistrar(sonarRuntime);
    RulesDefinition.Context context = new RulesDefinition.Context();
    RulesDefinition.NewRepository javaRepo = context
      .createRepository("java", "java")
      .setName("SonarAnalyzer");
    rulesDefinition.customRulesDefinition(context, javaRepo);
    javaRepo.done();

    RulesDefinition.Repository oldRepository = context.repository("squid");
    assertThat(oldRepository).isNull();

    RulesDefinition.Repository repository = context.repository(JavaSECheckRegistrar.REPOSITORY_KEY);

    assertThat(repository.name()).isEqualTo("Sonar");
    assertThat(repository.language()).isEqualTo("java");
    List<RulesDefinition.Rule> rules = repository.rules();
    assertThat(rules).hasSize(22);

    var activeByDefault = rules.stream()
      .filter(k -> !rulesNotActiveByDefault.contains(k.key()))
      .toList();
    var allRules = rules.stream().map(RulesDefinition.Rule::key).toList();

    assertThat(Arrays.asList(getRuleKeys())).containsExactlyInAnyOrderElementsOf(allRules);
    assertThat(activeByDefault).isNotEmpty().allMatch(RulesDefinition.Rule::activatedByDefault);
  }

  private static ActiveRules activeRules(String... repositoryAndKeys) {
    ActiveRulesBuilder activeRules = new ActiveRulesBuilder();
    for (String repositoryAndKey : repositoryAndKeys) {
      activeRules.addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.parse(repositoryAndKey))
        .setLanguage("java")
        .build());
    }
    return activeRules.build();
  }

  private static String[] getRuleKeysWithRepo() {
    var ruleKeys = getRuleKeys();
    for (int i = 0; i < ruleKeys.length; i++) {
      ruleKeys[i] = "java:" + ruleKeys[i];
    }
    return ruleKeys;
  }

  private static String[] getRuleKeys() {
    var ruleKeys = new ArrayList<String>();
    for (Class<? extends SECheck> check : JavaSECheckList.getNonOverriddenChecks()) {
      ruleKeys.add(check.getAnnotation(Rule.class).key());
    }
    return ruleKeys.toArray(new String[0]);
  }

}
