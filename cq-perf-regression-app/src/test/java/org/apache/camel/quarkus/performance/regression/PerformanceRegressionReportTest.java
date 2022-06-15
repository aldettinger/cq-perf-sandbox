package org.apache.camel.quarkus.performance.regression;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// @TODO: expecteds are dependent on System.newLine
public class PerformanceRegressionReportTest {

    @Test
    public void printAllShouldSucceed() throws IOException {
        PerformanceRegressionReport sut = new PerformanceRegressionReport("10m");
        sut.setCategoryMeasureForVersion("2.10.0", "JVM", 360.0);
        sut.setCategoryMeasureForVersion("2.8.0", "JVM", 380.0);
        sut.setCategoryMeasureForVersion("2.9.0", "JVM", 390.0);

        sut.setCategoryMeasureForVersion("2.10.0", "Native", 1000.0);
        sut.setCategoryMeasureForVersion("2.8.0", "Native", 1080.0);
        sut.setCategoryMeasureForVersion("2.9.0", "Native", 1090.0);

        String expected = IOUtils.resourceToString("/perf-regression-expecteds/nominal.txt", StandardCharsets.UTF_8);
        assertEquals(expected, sut.printAll());
    }

}
