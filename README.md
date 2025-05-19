Sonar Java symbolic execution plugin  [![Build Status](https://api.cirrus-ci.com/github/SonarSource/sonar-java-symbolic-execution.svg?branch=master)](https://cirrus-ci.com/github/SonarSource/sonar-java) [![Quality Gate](https://next.sonarqube.com/sonarqube/api/project_badges/measure?project=org.sonarsource.java%3Asonar-java-symbolic-execution&metric=alert_status)](https://next.sonarqube.com/sonarqube/dashboard?id=org.sonarsource.java%3Asonar-java-symbolic-execution) [![Coverage](https://next.sonarqube.com/sonarqube/api/project_badges/measure?project=org.sonarsource.java%3Asonar-java-symbolic-execution&metric=coverage)](https://next.sonarqube.com/sonarqube/component_measures/domain/Coverage?id=org.sonarsource.java%3Asonar-java-symbolic-execution)
==========

This SonarSource project is a plugin designed for advanced bug detection in Java projects, helping developers write [Clean Code](https://www.sonarsource.com/solutions/clean-code/).

Useful links
------------

* [Java Analyzer](https://github.com/SonarSource/sonar-java)
* [Issue tracking](https://jira.sonarsource.com/browse/SONARSE/)
* [Available rules](https://rules.sonarsource.com/java/tag/symbolic-execution/)
* [Sonar Community Forum](https://community.sonarsource.com/)

Have questions or feedback?
---------------------------

To provide feedback (request a feature, report a bug, etc.) use the [Sonar Community Forum](https://community.sonarsource.com/). Please do not forget to specify the language (Java!), plugin version and SonarQube Server version.

If you have a question on how to use plugin (and the [docs](https://docs.sonarsource.com/sonarqube-server/latest/analyzing-source-code/languages/java/) don't help you), we also encourage you to use the community forum.

Contributing
------------

### Topic in Sonar Community Forum

To request a new feature, please create a new thread in [Sonar Community Forum](https://community.sonarsource.com/). Even if you plan to implement it yourself and submit it back to the community, please start a new thread first to be sure that we can use it.

### Pull Request (PR)

To submit a contribution, create a pull request for this repository. Please make sure that you follow our [code style](https://github.com/SonarSource/sonar-developer-toolset#code-style) and all [tests](#testing) are passing (all checks must be green).

### Build the Project and Run Unit Tests

Requirements: Java 17

To build the plugin and run its unit tests, execute this command from the project's root directory:

    mvn clean install

### License

Copyright 2012-2025 SonarSource.

SonarQube analyzers released after November 29, 2024, including patch fixes for prior versions, are published under the [Sonar Source-Available License Version 1 (SSALv1)](LICENSE.txt).

See individual files for details that specify the license applicable to each file.
Files subject to the SSALv1 will be noted in their headers.
