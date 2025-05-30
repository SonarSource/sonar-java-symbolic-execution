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

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import org.sonar.check.Rule;
import org.sonar.java.se.checks.XxeProcessingCheck.XmlSetValidating;
import org.sonar.java.se.checks.XxeProcessingCheck.XxeEntityResolver;
import org.sonar.java.se.checks.XxeProperty.FeatureLoadExternalDtd;
import org.sonar.java.se.constraint.Constraint;
import org.sonar.java.se.constraint.ConstraintsByDomain;
import org.sonar.plugins.java.api.semantic.MethodMatchers;

import static org.sonar.java.se.checks.XxeProcessingCheck.PARSING_METHODS;

@Rule(key = "S6374")
public class XmlParserLoadsExternalSchemasCheck extends AbstractXMLProcessing {

  private static final List<Class<? extends Constraint>> DOMAINS = Arrays.asList(
    FeatureLoadExternalDtd.class,
    XmlSetValidating.class
  );

  @Override
  protected MethodMatchers getParsingMethods() {
    return PARSING_METHODS;
  }

  @Override
  protected boolean isUnSecuredByProperty(@Nullable ConstraintsByDomain constraintsByDomain) {
    if (constraintsByDomain == null) {
      return false;
    }
    return (constraintsByDomain.hasConstraint(FeatureLoadExternalDtd.UNSECURED) || constraintsByDomain.hasConstraint(XmlSetValidating.ENABLE))
      && !constraintsByDomain.hasConstraint(XxeEntityResolver.CUSTOM_ENTITY_RESOLVER);
  }

  @Override
  protected String getMessage() {
    return "Disable loading of external schemas in XML parsing.";
  }

  @Override
  protected boolean shouldTrackConstraint(Constraint constraint) {
    return constraint == FeatureLoadExternalDtd.UNSECURED || constraint == XmlSetValidating.ENABLE;
  }

  @Override
  protected List<Class<? extends Constraint>> getDomains() {
    return DOMAINS;
  }
}
