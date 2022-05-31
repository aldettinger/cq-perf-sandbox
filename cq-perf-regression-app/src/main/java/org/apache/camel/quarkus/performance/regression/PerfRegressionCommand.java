package org.apache.camel.quarkus.performance.regression;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RegExUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * # @TODO:
 * Implement detection of regression (5% variation seems reasonable)
 * Collect metrics and display report
 *
 * Add an option to switch off native mode
 * Validate parameters and options (business logic)
 *
 * Would we integrate this prototype in the main branch one day, we could make cq-perf-regression-app an example or a test, in order to detect breakage ?
 *
 * # @NOTES:
 * # We should be able to build with camel-quarkus version >= 2.5.0, note before due to CAMEL-QUARKUS-2578 fix (need to enforce the version with a check)
 * # We don't generate the cq-perf-regression-sample-app with the quarkus-maven-plugin so that we can test against a SNAPSHOT or release candidate versions (don't need to wait for quarkus-platform release)
 * # We automatically use the right maven version
 * # camel-quarkus >= 2.6.0.CR1 => maven 3.8.4
 * # camel-quarkus >= 2.1.0     => maven 3.8.1
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

        PerformanceRegressionReport report = new PerformanceRegressionReport(singleScenarioDuration);

        Path cqVersionsUnderTestFolder = Paths.get("target/cq-versions-under-test");
        try {
            Files.createDirectories(cqVersionsUnderTestFolder);
            FileUtils.cleanDirectory(cqVersionsUnderTestFolder.toFile());

            for (String cqVersion : cqVersions) {
                runPerfRegressionForCqVersion(cqVersionsUnderTestFolder.resolve(cqVersion), cqVersion, report);
            }

            System.out.println(report.printAll());
        } catch (IOException|XmlPullParserException e) {
            // Really needed, can't we just wrap as RuntimeException ?
            System.err.println("Can't run performance regression tests because an issue has been caught:");
            e.printStackTrace(System.err);
        }
    }

    private void runPerfRegressionForCqVersion(Path cqVersionUnderTestFolder, String cqVersion, PerformanceRegressionReport report) throws IOException, XmlPullParserException {

        // Copy the template project into a folder dedicated to cqVersion tests
        FileUtils.copyDirectory(PERF_SAMPLE_TEMPLATE_FOLDER.toFile(), cqVersionUnderTestFolder.toFile());

        FileEditionHelper.instantiateHyperfoilScenario(cqVersionUnderTestFolder, singleScenarioDuration);
        FileEditionHelper.instantiatePomFile(cqVersionUnderTestFolder, cqVersion, cqStagingRepository, camelStagingRepository);

        // Replace the maven version in maven wrapper
        String targetMavenVersion = getTargetMavenVersion(cqVersionUnderTestFolder);
        setMvnwMavenVersion(cqVersionUnderTestFolder, targetMavenVersion);

        // Run performance regression test in JVM mode
        double jvmThroughput = runPerfRegression(cqVersionUnderTestFolder, "integration-test");
        report.setCategoryMeasure(cqVersion, "JVM", jvmThroughput);

        // Run performance regression test in native mode
        double nativeThroughput = runPerfRegression(cqVersionUnderTestFolder, "integration-test -Dnative -Dquarkus.native.container-build=true");
        report.setCategoryMeasure(cqVersion, "Native", nativeThroughput);
    }

    private static String getTargetMavenVersion(Path cqVersionUnderTestFolder) {
        String stdout = MvnwCmdHelper.execute(cqVersionUnderTestFolder, "help:evaluate -Dexpression='target-maven-version' -q -DforceStdout");
        String targetMavenVersion = stdout.substring(stdout.lastIndexOf(System.lineSeparator())+System.lineSeparator().length());

        return "null object or invalid expression".equals(targetMavenVersion) ? "3.8.1" : targetMavenVersion;
    }

    private static void setMvnwMavenVersion(Path cqVersionUnderTestFolder, String targetMavenVersion) {
        MvnwCmdHelper.execute(cqVersionUnderTestFolder, "wrapper:wrapper -Dmaven="+targetMavenVersion);
    }

    private static double runPerfRegression(Path cqVersionUnderTestFolder, String args) {
        String stdout = MvnwCmdHelper.execute(cqVersionUnderTestFolder, args);

        // Extract the throughput from a log line like "15:26:23,110 INFO  (main) [i.h.m.RunMojo] Requests/sec: 1153.56"
        String throughput = RegExUtils.replacePattern(stdout, ".*RunMojo] Requests/sec: ([0-9.,]+).*", "$1");
        return Double.parseDouble(throughput);
    }

}
