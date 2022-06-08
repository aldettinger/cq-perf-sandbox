package org.apache.camel.quarkus.performance.regression;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.apache.maven.artifact.versioning.ComparableVersion;

import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;

/**
 * Provide a human readable performance regression report ready to be printed to the console.
 * For each camel-quarkus version, the report will print:
 * + The throughput for each measure in a category (JVM, Native) in a new column
 * + The percent increase throughput compared to the previous row in the same column 
 *
 * @TODO:
 * + Write a message when detecting a threshold > +/- 5% (possible performance regression detected, please consider to run double check with a long duration, like 1h)
 * + Explore another design where the table construction is fully dynamic across category and previous value can be retrieved locally, if any
 */
public class PerformanceRegressionReport {

    private String duration;
    private TreeMap<ComparableVersion, Map<String, Double>> measures = new TreeMap<>();

    public PerformanceRegressionReport(String duration) {
        this.duration = duration;
    }

    public void setCategoryMeasure(String cqVersion, String category, double throughput) {
        ComparableVersion version = new ComparableVersion(cqVersion);
        measures.computeIfAbsent(version, k -> new HashMap<>()).put(category, throughput);
    }

    public String printAll() {
        Table table = Table.create("Camel Quarkus Throughput Performance Increase Compared to Previous Version");

        StringColumn cqVersionsColumn = StringColumn.create("Camel Quarkus version");
        StringColumn durationsColumn = StringColumn.create("Duration");
        StringColumn jvmMeasuresColumn = StringColumn.create("JVM req/s [%increase]");
        StringColumn nativeMeasuresColumn = StringColumn.create("Native req/s [%increase]");
        StringColumn statusColumn = StringColumn.create("Status");
        double previousJvmMeasure = Double.POSITIVE_INFINITY;
        double previousNativeMeasure = Double.POSITIVE_INFINITY;

        for (Map.Entry<ComparableVersion, Map<String, Double>> measure : measures.entrySet()) {
            cqVersionsColumn.append(measure.getKey().toString());
            durationsColumn.append(duration);
            boolean regressionDetected = false;

            double jvmMeasure = measure.getValue().get("JVM");
            double percentIncreaseJvm = (previousJvmMeasure == Double.POSITIVE_INFINITY) ? 0.0 : ((jvmMeasure / previousJvmMeasure) - 1.0) * 100.0;
            jvmMeasuresColumn.append(String.format("%.2f req/s [%+.2f%%]", jvmMeasure, percentIncreaseJvm));
            previousJvmMeasure = jvmMeasure;
            if (percentIncreaseJvm <= -5.00) {
                regressionDetected = true;
            }

            if (measure.getValue().containsKey("Native")) {
                double nativeMeasure = measure.getValue().get("Native");
                double percentIncreaseNative = (previousNativeMeasure == Double.POSITIVE_INFINITY) ? 0.0 : ((nativeMeasure / previousNativeMeasure) - 1.0) * 100.0;
                nativeMeasuresColumn.append(String.format("%.2f req/s [%+.2f%%]", nativeMeasure, percentIncreaseNative));
                previousNativeMeasure = nativeMeasure;
                if (percentIncreaseNative <= -5.00) {
                    regressionDetected = true;
                }
            }

            statusColumn.append(regressionDetected ? "Potential performance regression" : "OK" );
        }

        table.addColumns(cqVersionsColumn, durationsColumn, jvmMeasuresColumn, nativeMeasuresColumn, statusColumn);

        return table.printAll();
    }

}
