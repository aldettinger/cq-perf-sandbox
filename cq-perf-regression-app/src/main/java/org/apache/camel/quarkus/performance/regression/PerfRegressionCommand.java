package org.apache.camel.quarkus.performance.regression;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.RegExUtils;

import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * # @TODO:
 * # Make the test duration customizable, with default to 10 minutes
 * # Add an option to switch off native mode
 * # Implement detection of regression (5% variation seems reasonable)
 * # Write stdout/stderr of perf test to file + stdout
 * # Collect metrics and display report
 * # Validate parameters and options (business logic)
 * # There are now a lot of cmd exec, should we refactor that ?
 *
 * # @NOTES:
 * # We should be able to build with camel-quarkus version >= 1.7.0 (as atlasmap was introduced that late)
 * # We don't build generate sample app with the quarkus-maven-plugin so that we can test against a SNAPSHOT or release candidate versions (don't need to wait for quarkus-platform release)
 * 
 * mvn versions matrix:
 * 2.6.0.CR1 => 3.8.4
 * 2.1.0 => 3.8.1
 * < 2.1.0 => 3.6.2
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

        File pomFile = cqVersionUnderTestFolder.resolve("pom.xml").toFile();
        String pomFileContent = FileUtils.readFileToString(pomFile, StandardCharsets.UTF_8);

        // Replace the parent version, camel-quarkus and camel staging repositories
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

    private static String getTargetMavenVersion(Path cqVersionUnderTestFolder) throws IOException {
        File mvnwFile = cqVersionUnderTestFolder.resolve("mvnw").toFile();
        CommandLine cmd = CommandLine.parse(mvnwFile.getAbsolutePath()+ " help:evaluate -Dexpression='target-maven-version' -q -DforceStdout");
        DefaultExecutor executor = new DefaultExecutor();
        ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
        PumpStreamHandler psh = new PumpStreamHandler(stdoutStream);
        executor.setStreamHandler(psh);
        executor.setWorkingDirectory(cqVersionUnderTestFolder.toFile());

        // @TODO: is it output ? or stderr or stdout ? maybe both
        String stdout;
        try {
            int exitValue = executor.execute(cmd);
            stdout = stdoutStream.toString(StandardCharsets.UTF_8);
            if(exitValue != 0) {
                System.err.println(stdout);
                throw new RuntimeException("The command '" + cmd +"' has returned exitValue "+exitValue+", process logs below:\n"+stdout);
            }
        }
        catch(ExecuteException eex) {
            stdout = stdoutStream.toString(StandardCharsets.UTF_8);
            throw new RuntimeException("The command '" + cmd +"' has thrown ExecuteException, process logs below:\n"+stdout);
        }
        String targetMavenVersion = stdout.substring(stdout.lastIndexOf(System.lineSeparator())+System.lineSeparator().length());

        return "null object or invalid expression".equals(targetMavenVersion) ? "3.6.2" : targetMavenVersion;
    }

    private static void setMvnwMavenVersion(Path cqVersionUnderTestFolder, String targetMavenVersion) throws IOException {
        File mvnwFile = cqVersionUnderTestFolder.resolve("mvnw").toFile();
        CommandLine cmd = CommandLine.parse(mvnwFile.getAbsolutePath()+ " wrapper:wrapper -Dmaven="+targetMavenVersion);
        DefaultExecutor executor = new DefaultExecutor();
        ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
        PumpStreamHandler psh = new PumpStreamHandler(stdoutStream);
        executor.setStreamHandler(psh);
        executor.setWorkingDirectory(cqVersionUnderTestFolder.toFile());

        // @TODO: is it output ? or stderr or stdout ? maybe both
        String stdout;
        try {
            int exitValue = executor.execute(cmd);
            stdout = stdoutStream.toString(StandardCharsets.UTF_8);
            if(exitValue != 0) {
                System.err.println(stdout);
                throw new RuntimeException("The command '" + cmd +"' has returned exitValue "+exitValue+", process logs below:\n"+stdout);
            }
        }
        catch(ExecuteException eex) {
            stdout = stdoutStream.toString(StandardCharsets.UTF_8);
            throw new RuntimeException("The command '" + cmd +"' has thrown ExecuteException, process logs below:\n"+stdout);
        }
    }

    private static void runPerfRegression(Path cqVersionUnderTestFolder, String args) throws IOException {
        File mvnwFile = cqVersionUnderTestFolder.resolve("mvnw").toFile();
        CommandLine cmd = CommandLine.parse(mvnwFile.getAbsolutePath()+ " "+ args);
        DefaultExecutor executor = new DefaultExecutor();
        ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
        PumpStreamHandler psh = new PumpStreamHandler(stdoutStream);
        executor.setStreamHandler(psh);
        executor.setWorkingDirectory(cqVersionUnderTestFolder.toFile());

        // @TODO: is it output ? or stderr or stdout ? maybe both
        String stdout;
        try {
            int exitValue = executor.execute(cmd);
            stdout = stdoutStream.toString(StandardCharsets.UTF_8);
            if(exitValue != 0) {
                System.err.println(stdout);
                throw new RuntimeException("The command '" + cmd +"' has returned exitValue "+exitValue+", process logs below:\n"+stdout);
            }
        }
        catch(ExecuteException eex) {
            stdout = stdoutStream.toString(StandardCharsets.UTF_8);
            throw new RuntimeException("The command '" + cmd +"' has thrown ExecuteException, process logs below:\n"+stdout);
        }

        System.out.println("-----------------------------------------");

        // Extract the throughput from a log line like "15:26:23,110 INFO  (main) [i.h.m.RunMojo] Requests/sec: 1153.56"
        String throughput = RegExUtils.replacePattern(stdout, ".*RunMojo] Requests/sec: ([0-9.,]+).*", "$1");
        throughput.toString();

        System.out.println("Parsed throughput = '"+throughput+"' Req/s");
        System.out.println("-----------------------------------------");
    }

}
