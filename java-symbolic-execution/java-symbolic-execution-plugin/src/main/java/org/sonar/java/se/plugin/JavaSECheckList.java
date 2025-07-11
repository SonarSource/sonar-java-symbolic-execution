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

import java.util.List;
import org.sonar.java.se.checks.AllowXMLInclusionCheck;
import org.sonar.java.se.checks.BooleanGratuitousExpressionsCheck;
import org.sonar.java.se.checks.ConditionalUnreachableCodeCheck;
import org.sonar.java.se.checks.CustomUnclosedResourcesCheck;
import org.sonar.java.se.checks.DenialOfServiceXMLCheck;
import org.sonar.java.se.checks.InvariantReturnCheck;
import org.sonar.java.se.checks.LocksNotUnlockedCheck;
import org.sonar.java.se.checks.MapComputeIfAbsentOrPresentCheck;
import org.sonar.java.se.checks.MinMaxRangeCheck;
import org.sonar.java.se.checks.NoWayOutLoopCheck;
import org.sonar.java.se.checks.NonNullSetToNullCheck;
import org.sonar.java.se.checks.ObjectOutputStreamCheck;
import org.sonar.java.se.checks.OptionalGetBeforeIsPresentCheck;
import org.sonar.java.se.checks.ParameterNullnessCheck;
import org.sonar.java.se.checks.RedundantAssignmentsCheck;
import org.sonar.java.se.checks.SECheck;
import org.sonar.java.se.checks.StreamConsumedCheck;
import org.sonar.java.se.checks.StreamNotConsumedCheck;
import org.sonar.java.se.checks.UnclosedResourcesCheck;
import org.sonar.java.se.checks.XmlParserLoadsExternalSchemasCheck;
import org.sonar.java.se.checks.XmlValidatedSignatureCheck;
import org.sonar.java.se.checks.XxeProcessingCheck;

public class JavaSECheckList {

  private JavaSECheckList(){
    // no need to instantiate
  }

  public static List<Class<? extends SECheck>> getChecks() {
    return List.of(
      // SEChecks ordered by ExplodedGraphWalker need
      UnclosedResourcesCheck.class,
      LocksNotUnlockedCheck.class,
      NonNullSetToNullCheck.class,
      NoWayOutLoopCheck.class,
      OptionalGetBeforeIsPresentCheck.class,
      StreamConsumedCheck.class,
      RedundantAssignmentsCheck.class,
      XxeProcessingCheck.class,
      // SEChecks Depending on XxeProcessingCheck
      DenialOfServiceXMLCheck.class,
      AllowXMLInclusionCheck.class,
      XmlParserLoadsExternalSchemasCheck.class,

      // SEChecks not require by ExplodedGraphWalker, from the fastest to the slowest
      ParameterNullnessCheck.class,
      BooleanGratuitousExpressionsCheck.class,
      ConditionalUnreachableCodeCheck.class,
      XmlValidatedSignatureCheck.class,
      CustomUnclosedResourcesCheck.class,
      MapComputeIfAbsentOrPresentCheck.class,
      InvariantReturnCheck.class,
      StreamNotConsumedCheck.class,
      ObjectOutputStreamCheck.class,
      MinMaxRangeCheck.class);
  }

}
