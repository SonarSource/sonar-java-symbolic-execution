/*
 * SonarQube Java
 * Copyright (C) 2012-2025 SonarSource Sàrl
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
package org.sonar.java.cfg;

import java.util.Map;
import javax.annotation.Nullable;

public class CfgUtilsTest {
  void foo(@Nullable Map<String, String> map) {
    map.size(); // "A "NullPointerException" could be thrown; "map" is nullable here.
  }

  int divide(int num) {
    int dem = 0;
    if ((num % 2) == 0) {
      dem = 1;
    }
    return num / dem;
  }

  void testFoo() {
    foo(null);
    divide(10);
  }
}
