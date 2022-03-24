package org.apache.camel.quarkus.performance.regression;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.RegExUtils;

/**
 * # @TODO:
 * # Make the test duration customizable ?
 * # Improve the staging support, today we need to hard code it manually in the pom.xml files
 * # Implement detection of regression (5% variation seems reasonable)
 * # Upgrade to hyperfoil-maven-plugin 0.19 (should be able to remove the dep to hyperfoil-test-suite
 * # Write stdout/stderr of perf test to file + stdout
 * # Collect metrics and display report
 * 
 * # @NOTES:
 * # We should be able to build with camel-quarkus version >= 1.7.0 (as atlasmap was introduced that late)
 * # We don't build using the quarkus-maven-plugin so that we can test against a SNAPSHOT or release candidate versions (don't need to wait for quarkus-platform release)
 */
public class Main {

    private static Path cqPerfRegressionSampleBase = Paths.get("cq-perf-regression-sample-base");

    public static void main(String[] args) throws IOException {

        // TODO: remove, debug helper only
//        args = new String[] {"2.7.0"};

        if (args.length == 0) {
            System.out.println("This tool applies a performance test against a list of camel-quarkus versions. As such, it should be able to detect performance regressions.");
            System.out.println("A list of camel-quarkus versions to be tested should be provided, e.g: 2.4.0 2.5.0");
            System.exit(1);
        }

        Path cqVersionsUnderTestFolder = Paths.get("target/cq-versions-under-test");
        Files.createDirectories(cqVersionsUnderTestFolder);
        FileUtils.cleanDirectory(cqVersionsUnderTestFolder.toFile());

        for (String cqVersion : args) {
            runPerfRegressionForCqVersion(cqVersionsUnderTestFolder.resolve(cqVersion), cqVersion);
        }
    }

    public static void runPerfRegressionForCqVersion(Path cqVersionUnderTestFolder, String cqVersion) throws IOException {

        // Copy the base project into a folder dedicated to cqVersion tests
        FileUtils.copyDirectory(cqPerfRegressionSampleBase.toFile(), cqVersionUnderTestFolder.toFile());

        // Replace the parent version
        File pomFile = cqVersionUnderTestFolder.resolve("pom.xml").toFile();
        String fileString = FileUtils.readFileToString(pomFile, StandardCharsets.UTF_8);
        String finalString = fileString.replaceAll("ce52c658-292c-461f-968b-5930dae42629", cqVersion);
        FileUtils.writeStringToFile(pomFile, finalString, StandardCharsets.UTF_8);

        // Run JVM mode perf test
        runPerfRegression(cqVersionUnderTestFolder, "integration-test");

        // Run native mode perf test
        runPerfRegression(cqVersionUnderTestFolder, "integration-test -Dnative -Dquarkus.native.container-build=true");
    }

    public static void runPerfRegression(Path cqVersionUnderTestFolder, String args) throws IOException {
        File mvnwFile = cqVersionUnderTestFolder.resolve("mvnw").toFile();
        CommandLine cmd = CommandLine.parse(mvnwFile.getAbsolutePath()+ " "+ args);
        DefaultExecutor executor = new DefaultExecutor();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PumpStreamHandler psh = new PumpStreamHandler(stdout);
        executor.setStreamHandler(psh);
        executor.setWorkingDirectory(cqVersionUnderTestFolder.toFile());
        int exitValue = executor.execute(cmd);
        String output = stdout.toString(StandardCharsets.UTF_8);

        System.out.println("-----------------------------------------");
//        System.out.println(output);

        if(exitValue != 0) {
            System.err.println(output);
            throw new RuntimeException("The command '" + cmd +"' has failed");
        }

        // Extract the throughput from a line like "15:26:23,110 INFO  (main) [i.h.m.RunMojo] Requests/sec: 1153.56"
        String throughput = RegExUtils.replacePattern(output, ".*RunMojo] Requests/sec: ([0-9.,]+).*", "$1");
        throughput.toString();

        System.out.println("Parsed throughput = '"+throughput+"' Req/s");
        System.out.println("-----------------------------------------");
    }

}
