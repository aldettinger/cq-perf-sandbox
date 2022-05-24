package org.apache.camel.quarkus.performance.regression;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RegExUtils;

import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * # @TODO:
 * Implement detection of regression (5% variation seems reasonable)
 * Collect metrics and display report
 *
 * Add an option to switch off native mode
 * Validate parameters and options (business logic)
 * Template file edition, find a library to edit XML and YAML files => Stop using GUID replacement.
 * org.yaml/snakeyaml ? Load/Edit/Save ? license ?
 * Ideally, we would not include a staging repo with a fake guid when not needed. today it generates warning.
 *
 * Would we integrate this prototype in the main brench one day, we could make cq-perf-regression-app an example, in order to detect breakage ?
 *
 * # @NOTES:
 * # We should be able to build with camel-quarkus version >= 2.5.0, after CAMEL-QUARKUS-2578 fix (need to enforce the version with a check)
 * # We don't generate the cq-perf-regression-sample-app with the quarkus-maven-plugin so that we can test against a SNAPSHOT or release candidate versions (don't need to wait for quarkus-platform release)
 *
 * mvn versions matrix:
 * 2.6.0.CR1 => 3.8.4
 * 2.1.0 => 3.8.1
 */
@picocli.CommandLine.Command(description = "Run a performance test against a list of Camel Quarkus versions")
public class PerfRegressionCommand implements Runnable {

    private static Path PERF_SAMPLE_TEMPLATE_FOLDER = Paths.get("cq-perf-regression-sample-base");

    @Parameters(paramLabel = "<versions>", arity = "1..*", description = "A list of versions, e.g: 2.7.0 2.8.0-SNAPSHOT")
    private String[] cqVersions = {};

    @Option(names = {"-cqs", "--camel-quarkus-staging-repository"}, description = "Camel Quarkus staging repository, e.g: https://repository.apache.org/content/repositories/orgapachecamel-1423")
    private String cqStagingRepository;

    @Option(names = {"-cs", "--camel-staging-repository"}, description = "Camel staging repository, e.g: https://repository.apache.org/content/repositories/orgapachecamel-1424")
    private String camelStagingRepository;

    @Option(names = {"-d", "--duration"}, defaultValue = "10m", description = "The duration of a single performance test scenario (e.g. 45s, 30m, 1h). Up to 2 scenarios per version could be run.")
    private String singleScenarioDuration;

    @Override
    public void run() {

        Path cqVersionsUnderTestFolder = Paths.get("target/cq-versions-under-test");
        try {
            Files.createDirectories(cqVersionsUnderTestFolder);
            FileUtils.cleanDirectory(cqVersionsUnderTestFolder.toFile());

            for (String cqVersion : cqVersions) {
                runPerfRegressionForCqVersion(cqVersionsUnderTestFolder.resolve(cqVersion), cqVersion);
            }
        } catch (IOException e) {
            System.err.println("Can't run performance regression tests because an issue has been caught:");
            e.printStackTrace(System.err);
        }
    }

    private void runPerfRegressionForCqVersion(Path cqVersionUnderTestFolder, String cqVersion) throws IOException {

        // Copy the template project into a folder dedicated to cqVersion tests
        FileUtils.copyDirectory(PERF_SAMPLE_TEMPLATE_FOLDER.toFile(), cqVersionUnderTestFolder.toFile());

        File benchmarkFile = cqVersionUnderTestFolder.resolve("cq-perf-regression-scenario.hf.yaml").toFile();
        String benchmarkFileContent = FileUtils.readFileToString(benchmarkFile, StandardCharsets.UTF_8);
        benchmarkFileContent = benchmarkFileContent.replaceAll("372f6453-7527-43b1-850b-3824fc3d1187", singleScenarioDuration);
        FileUtils.writeStringToFile(benchmarkFile, benchmarkFileContent, StandardCharsets.UTF_8);

        File pomFile = cqVersionUnderTestFolder.resolve("pom.xml").toFile();
        String pomFileContent = FileUtils.readFileToString(pomFile, StandardCharsets.UTF_8);

        // Replace the parent version, camel-quarkus staging repository and fianlly camel staging repository
        pomFileContent = pomFileContent.replaceAll("ce52c658-292c-461f-968b-5930dae42629", cqVersion);
        if(cqStagingRepository != null) {
            pomFileContent = pomFileContent.replaceAll("2d114490-c5a8-4c61-b960-4283811d2405", cqStagingRepository);
        }
        if(camelStagingRepository != null) {
            pomFileContent = pomFileContent.replaceAll("2d114490-c5a8-4c61-b960-4283811d2405", camelStagingRepository);
        }
        FileUtils.writeStringToFile(pomFile, pomFileContent, StandardCharsets.UTF_8);

        // Replace the maven version in maven wrapper
        String targetMavenVersion = getTargetMavenVersion(cqVersionUnderTestFolder);
        setMvnwMavenVersion(cqVersionUnderTestFolder, targetMavenVersion);

        // Run performance regression test in JVM mode
        runPerfRegression(cqVersionUnderTestFolder, "integration-test");

        // Run performance regression test in native mode
        runPerfRegression(cqVersionUnderTestFolder, "integration-test -Dnative -Dquarkus.native.container-build=true");
    }

    private static String getTargetMavenVersion(Path cqVersionUnderTestFolder) {
        String stdout = MvnwCmdHelper.execute(cqVersionUnderTestFolder, "help:evaluate -Dexpression='target-maven-version' -q -DforceStdout");
        String targetMavenVersion = stdout.substring(stdout.lastIndexOf(System.lineSeparator())+System.lineSeparator().length());

        return "null object or invalid expression".equals(targetMavenVersion) ? "3.8.1" : targetMavenVersion;
    }

    private static void setMvnwMavenVersion(Path cqVersionUnderTestFolder, String targetMavenVersion) {
        MvnwCmdHelper.execute(cqVersionUnderTestFolder, "wrapper:wrapper -Dmaven="+targetMavenVersion);
    }

    private static void runPerfRegression(Path cqVersionUnderTestFolder, String args) {
        String stdout = MvnwCmdHelper.execute(cqVersionUnderTestFolder, args);

        System.out.println("-----------------------------------------");

        // Extract the throughput from a log line like "15:26:23,110 INFO  (main) [i.h.m.RunMojo] Requests/sec: 1153.56"
        String throughput = RegExUtils.replacePattern(stdout, ".*RunMojo] Requests/sec: ([0-9.,]+).*", "$1");
        throughput.toString();

        System.out.println("Parsed throughput = '"+throughput+"' Req/s");
        System.out.println("-----------------------------------------");
    }

}
