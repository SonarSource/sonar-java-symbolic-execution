/*
 * SonarQube Java
 * Copyright (C) 2013-2025 SonarSource SA
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
package org.sonar.java.it;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.sonar.orchestrator.build.Build;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.build.SonarScanner;
import com.sonar.orchestrator.container.Edition;
import com.sonar.orchestrator.container.Server;
import com.sonar.orchestrator.junit4.OrchestratorRule;
import com.sonar.orchestrator.junit4.OrchestratorRuleBuilder;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.MavenLocation;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.assertj.core.api.Fail;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.java.test.classpath.TestClasspathUtils;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaRulingTest {

  private static final int LOGS_NUMBER_LINES = 200;
  private static final Logger LOG = LoggerFactory.getLogger(JavaRulingTest.class);

  private static final ImmutableSet<String> SUBSET_OF_ENABLED_RULES = ImmutableSet.of(
    "S2095", "S2189", "S2222", "S2583", "S2589", "S2637", "S2689", "S2755", "S3065",
    "S3516", "S3546", "S3655", "S3824", "S3958", "S3959", "S4165", "S4449", "S6373", "S6374", "S6376", "S6377");

  @ClassRule
  public static TemporaryFolder tmpDumpOldFolder = new TemporaryFolder();

  private static Path effectiveDumpOldFolder;

  @ClassRule
  public static final OrchestratorRule ORCHESTRATOR = createOrchestrator();

  private static OrchestratorRule createOrchestrator() {
    OrchestratorRuleBuilder orchestratorBuilder = OrchestratorRule.builderEnv()
      .setEdition(Edition.ENTERPRISE_LW)
      .activateLicense()
      .useDefaultAdminCredentialsForBuilds(true)
      .setSonarVersion(System.getProperty("sonar.runtimeVersion", "LATEST_RELEASE"))
      .addPlugin(MavenLocation.of("org.sonarsource.java", "sonar-java-plugin", System.getProperty("sonar.java.version", "DEV")))
      .addPlugin(FileLocation.of(TestClasspathUtils.findModuleJarPath("../../java-symbolic-execution/java-symbolic-execution-plugin").toFile()))
      .addPlugin(MavenLocation.of("org.sonarsource.sonar-lits-plugin", "sonar-lits-plugin", "0.11.0.2659"));

    return orchestratorBuilder.build();
  }

  @AfterClass
  public static void afterAllAnalysis() throws IOException {
    PerformanceStatistics.generate(Paths.get("target", "performance"));
  }

  @BeforeClass
  public static void prepare() throws Exception {
    Set<String> result = new HashSet<>();
    List<String> extraNonDefaultRules = List.of("S3546", "S6374");
    ProfileGenerator.generate(ORCHESTRATOR, "Sonar Way", ImmutableMap.of(), new HashSet<>(), SUBSET_OF_ENABLED_RULES, result,
      extraNonDefaultRules);
    assertThat(result).hasSize(21); // ALL symbolic-execution rules

    Path allRulesFolder = Paths.get("src/test/resources");
    effectiveDumpOldFolder = tmpDumpOldFolder.getRoot().toPath().toAbsolutePath();
    Files.list(allRulesFolder)
      .filter(p -> p.toFile().isDirectory())
      .forEach(srcProjectDir -> copyDumpSubset(srcProjectDir, effectiveDumpOldFolder.resolve(srcProjectDir.getFileName())));
  }

  private static void copyDumpSubset(Path srcProjectDir, Path dstProjectDir) {
    try {
      Files.createDirectory(dstProjectDir);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create directory: " + dstProjectDir);
    }
    SUBSET_OF_ENABLED_RULES.stream()
      .map(ruleKey -> srcProjectDir.resolve("java-" + ruleKey + ".json"))
      .filter(p -> p.toFile().exists())
      .forEach(srcJsonFile -> copyFile(srcJsonFile, dstProjectDir));
  }

  private static void copyFile(Path source, Path targetDir) {
    try {
      Files.copy(source, targetDir.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to copy file: " + source);
    }
  }

  @Test
  public void guava() throws Exception {
    String projectName = "guava";
    String projectKey = "com.google.guava:guava";
    MavenBuild build = test_project(projectKey, projectName);
    build
      // by default guava is compatible with java 6, however this is not supported with JDK 17
      .setProperty("java.version", "1.7")
      .setProperty("maven.javadoc.skip", "true")
      // use batch
      .setProperty("sonar.java.experimental.batchModeSizeInKB", "8192");
    executeBuildWithCommonProperties(build, projectName);
  }

  @Test
  public void eclipse_jetty() throws Exception {
    List<String> dirs = Arrays.asList("jetty-http/", "jetty-io/", "jetty-jmx/", "jetty-server/", "jetty-slf4j-impl/", "jetty-util/",
      "jetty-util-ajax/", "jetty-xml/", "tests" +
      "/jetty-http-tools/");

    String mainBranchSourceCode = "eclipse-jetty";
    String mainBinaries = dirs.stream().map(dir -> FileLocation.of("../sources/" + mainBranchSourceCode + "/" + dir + "target/classes"))
      .map(JavaRulingTest::getFileLocationAbsolutePath)
      .collect(Collectors.joining(","));

    MavenBuild branchBuild = test_project("org.eclipse.jetty:jetty-project", mainBranchSourceCode)
      // re-define binaries from initial maven build
      .setProperty("sonar.java.binaries", mainBinaries)
      .setProperty("sonar.exclusions", "jetty-server/src/main/java/org/eclipse/jetty/server/HttpInput.java," +
        "jetty-osgi/jetty-osgi-boot/src/main/java/org/eclipse/jetty/osgi/boot/internal/serverfactory/ServerInstanceWrapper.java")
      .addArgument("-Dpmd.skip=true")
      .addArgument("-Dcheckstyle.skip=true");

    executeBuildWithCommonProperties(branchBuild, mainBranchSourceCode);
  }

  private static String getFileLocationAbsolutePath(FileLocation location) {
    try {
      return location.getFile().getCanonicalFile().getAbsolutePath();
    } catch (IOException e) {
      return "";
    }
  }


  @Test
  public void apache_commons_beanutils() throws Exception {
    String projectName = "commons-beanutils";
    MavenBuild build = test_project("commons-beanutils:commons-beanutils", projectName);
    build
      // by default it can not be built with jdk 17 without changing some plugin versions
      .setProperty("maven-bundle-plugin.version", "5.1.4")
      // use batch
      .setProperty("sonar.java.experimental.batchModeSizeInKB", "8192");
    executeBuildWithCommonProperties(build, projectName);
  }

  @Test
  public void sonarqube_server() throws Exception {
    // sonarqube-6.5/server/sonar-server (v.6.5)
    String projectName = "sonar-server";
    MavenBuild build = test_project("org.sonarsource.sonarqube:sonar-server", "sonarqube-6.5/server", projectName)
      .setProperty("sonar.java.fileByFile", "true");
    executeBuildWithCommonProperties(build, projectName);
  }

  @Test
  public void jboss_ejb3_tutorial() throws Exception {
    // https://github.com/jbossejb3/jboss-ejb3-tutorial (18/01/2015)
    String projectName = "jboss-ejb3-tutorial";
    prepareProject(projectName, projectName);
    SonarScanner build = SonarScanner.create(FileLocation.of("../sources/jboss-ejb3-tutorial").getFile())
      .setProperty("sonar.scanner.skipJreProvisioning", "true")
      .setProperty("sonar.java.fileByFile", "true")
      .setProjectKey(projectName)
      .setProjectName(projectName)
      .setProjectVersion("0.1.0-SNAPSHOT")
      .setSourceEncoding("UTF-8")
      .setSourceDirs(".")
      .setDebugLogs(true)
      // Dummy sonar.java.binaries to pass validation
      .setProperty("sonar.java.binaries", "asynch")
      .setProperty("sonar.java.source", "1.5");
    executeDebugBuildWithCommonProperties(build, projectName);
  }

  @Test
  public void regex_examples() throws IOException {
    String projectName = "regex-examples";
    MavenBuild build = test_project("org.regex-examples:regex-examples", projectName)
      .setProperty("sonar.java.fileByFile", "true");
    executeBuildWithCommonProperties(build, projectName);
  }

  private static MavenBuild test_project(String projectKey, String projectName) throws IOException {
    return test_project(projectKey, null, projectName);
  }

  private static MavenBuild test_project(String projectKey, @Nullable String path, String projectName) throws IOException {
    String pomLocation = "../sources/" + (path != null ? path + "/" : "") + projectName + "/pom.xml";
    File pomFile = FileLocation.of(pomLocation).getFile().getCanonicalFile();
    prepareProject(projectKey, projectName);
    MavenBuild mavenBuild = MavenBuild.create().setPom(pomFile).setCleanPackageSonarGoals().addArgument("-DskipTests");
    mavenBuild.setProperty("sonar.scanner.skipJreProvisioning", "true");
    mavenBuild.setProperty("sonar.projectKey", projectKey);
    return mavenBuild;
  }

  private static void prepareProject(String projectKey, String projectName) {
    ORCHESTRATOR.getServer().provisionProject(projectKey, projectName);
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "java", "rules");
  }

  private static void executeDebugBuildWithCommonProperties(Build<?> build, String projectName) throws IOException {
    executeBuildWithCommonProperties(build, projectName, true);
  }

  private static void executeBuildWithCommonProperties(Build<?> build, String projectName) throws IOException {
    executeBuildWithCommonProperties(build, projectName, false);
  }

  private static void executeBuildWithCommonProperties(Build<?> build, String projectName, boolean buildQuietly) throws IOException {
    build.setProperty("sonar.cpd.exclusions", "**/*")
      .setProperty("sonar.java.performance.measure", "true")
      .setProperty("sonar.java.performance.measure.path", "target/performance/sonar.java.performance.measure.json")
      .setProperty("sonar.import_unknown_files", "true")
      .setProperty("sonar.skipPackageDesign", "true")
      .setProperty("sonar.lits.dump.old", effectiveDumpOldFolder.resolve(projectName).toString())
      .setProperty("sonar.lits.dump.new", FileLocation.of("target/actual/" + projectName).getFile().getAbsolutePath())
      .setProperty("sonar.lits.differences", litsDifferencesPath(projectName))
      .setProperty("sonar.internal.analysis.failFast", "true");
    BuildResult buildResult;
    if (buildQuietly) {
      // if build fail, ruling job is not violently interrupted, allowing time to dump SQ logs
      buildResult = ORCHESTRATOR.executeBuildQuietly(build);
    } else {
      buildResult = ORCHESTRATOR.executeBuild(build);
    }
    if (buildResult.isSuccess()) {
      assertNoDifferences(projectName);
    } else {
      dumpServerLogs();
      Fail.fail("Build failure for project: " + projectName);
    }
  }

  private static void dumpServerLogs() throws IOException {
    Server server = ORCHESTRATOR.getServer();
    LOG.error("::::::::::::::::::::::::::::::::::: DUMPING SERVER LOGS :::::::::::::::::::::::::::::::::::");
    dumpServerLogLastLines(server.getAppLogs());
    dumpServerLogLastLines(server.getCeLogs());
    dumpServerLogLastLines(server.getEsLogs());
    dumpServerLogLastLines(server.getWebLogs());
  }

  private static void dumpServerLogLastLines(File logFile) throws IOException {
    if (!logFile.exists()) {
      return;
    }
    List<String> logs = Files.readAllLines(logFile.toPath());
    int nbLines = logs.size();
    if (nbLines > LOGS_NUMBER_LINES) {
      logs = logs.subList(nbLines - LOGS_NUMBER_LINES, nbLines);
    }
    LOG.error("=================================== START {} ===================================", logFile.getName());
    LOG.error("{}{}", System.lineSeparator(), logs.stream().collect(Collectors.joining(System.lineSeparator())));
    LOG.error("===================================== END {} ===================================", logFile.getName());
  }

  private static String litsDifferencesPath(String projectName) {
    return FileLocation.of("target/" + projectName + "_differences").getFile().getAbsolutePath();
  }

  private static void assertNoDifferences(String projectName) throws IOException {

    String differences = Files.readString(Paths.get(litsDifferencesPath(projectName)));
    assertThat(differences).isEmpty();
  }

  static WsClient newAdminWsClient(OrchestratorRule orchestrator) {
    return WsClientFactories.getDefault().newClient(HttpConnector.newBuilder()
      .credentials(Server.ADMIN_LOGIN, Server.ADMIN_PASSWORD)
      .url(orchestrator.getServer().getUrl())
      .build());
  }
}
