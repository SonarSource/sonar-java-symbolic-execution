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


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sonar.api.SonarEdition;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.SonarRuntime;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.internal.SonarRuntimeImpl;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.utils.Version;
import org.sonar.check.Rule;
import org.sonar.java.checks.verifier.TestCheckRegistrarContext;
import org.sonar.java.se.checks.SECheck;
import org.sonar.plugins.java.api.CheckRegistrar;
import org.sonar.plugins.javasymbolicexecution.api.JavaRuleReplacements;
import org.sonar.scanner.plugin.api.impl.rule.ActiveRulesBuilder;
import org.sonar.scanner.plugin.api.impl.rule.NewActiveRule;

import static org.assertj.core.api.Assertions.assertThat;

class JavaSECheckRegistrarTest {

  private static final ActiveRules activeRules = activeRules(getRuleKeysWithRepo());

  private static final List<String> rulesNotActiveByDefault = List.of(
    "S2583",
    "S2589",
    "S3546",
    "S6374"
  );

  @Test
  void register_rules() {
    CheckRegistrar registrar = new JavaSECheckRegistrar(null);
    TestCheckRegistrarContext context = new TestCheckRegistrarContext();

    CheckFactory checkFactory = new CheckFactory(activeRules);
    registrar.register(context, checkFactory);

    assertThat(context.mainRuleKeys).map(RuleKey::toString).containsExactlyInAnyOrder(getRuleKeysWithRepo());
    assertThat(context.testRuleKeys).isEmpty();
  }

  @Test
  void register_rules_excludes_replaced_rules() {
    JavaRuleReplacements replacements = () -> Set.of(RuleKey.of("java", "S2259"), RuleKey.of("java", "S3518"));
    CheckRegistrar registrar = new JavaSECheckRegistrar(null, new JavaRuleReplacements[] {replacements});
    TestCheckRegistrarContext context = new TestCheckRegistrarContext();

    registrar.register(context, new CheckFactory(activeRules));

    assertThat(context.mainRuleKeys).map(RuleKey::toString)
      .doesNotContain("java:S2259", "java:S3518")
      .hasSize(21);
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
    assertThat(rules).hasSize(23);

    var activeByDefault = rules.stream()
      .filter(k -> !rulesNotActiveByDefault.contains(k.key()))
      .toList();
    var allRules = rules.stream().map(RulesDefinition.Rule::key).toList();

    assertThat(Arrays.asList(getRuleKeys())).containsExactlyInAnyOrderElementsOf(allRules);
    assertThat(activeByDefault).isNotEmpty().allMatch(RulesDefinition.Rule::activatedByDefault);
  }

  @Test
  void rules_definition_excludes_replaced_rules() {
    SonarRuntime sonarRuntime = SonarRuntimeImpl.forSonarQube(Version.create(10, 2), SonarQubeSide.SERVER, SonarEdition.ENTERPRISE);
    JavaRuleReplacements replacements = () -> Set.of(RuleKey.of("java", "S3546"), RuleKey.of("java", "S3655"), RuleKey.of("java", "S3959"));
    JavaSECheckRegistrar rulesDefinition = new JavaSECheckRegistrar(sonarRuntime, new JavaRuleReplacements[] {replacements});
    RulesDefinition.Context context = new RulesDefinition.Context();
    RulesDefinition.NewRepository javaRepo = context.createRepository("java", "java").setName("SonarAnalyzer");

    rulesDefinition.customRulesDefinition(context, javaRepo);
    javaRepo.done();

    assertThat(context.repository("java").rules())
      .extracting(RulesDefinition.Rule::key)
      .doesNotContain("S3546", "S3655", "S3959")
      .hasSize(20);
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
    for (Class<? extends SECheck> check : JavaSECheckList.getChecks()) {
      ruleKeys.add(check.getAnnotation(Rule.class).key());
    }
    return ruleKeys.toArray(new String[0]);
  }

}
