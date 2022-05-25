package org.apache.camel.quarkus.performance.regression;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public class FileEditionHelper {

    // velocity ?
    public static void instantiateHyperfoilScenario(Path cqVersionUnderTestFolder, String singleScenarioDuration) throws IOException {
        File benchmarkFile = cqVersionUnderTestFolder.resolve("cq-perf-regression-scenario.hf.yaml").toFile();
        String benchmarkFileContent = FileUtils.readFileToString(benchmarkFile, StandardCharsets.UTF_8);
        benchmarkFileContent = benchmarkFileContent.replaceAll("372f6453-7527-43b1-850b-3824fc3d1187", singleScenarioDuration);
        FileUtils.writeStringToFile(benchmarkFile, benchmarkFileContent, StandardCharsets.UTF_8);
    }

    public static void instantiatePomFile(Path cqVersionUnderTestFolder, String cqVersion, String cqStagingRepositoryUrl, String camelStagingRepositoryUrl)
        throws IOException, XmlPullParserException {
        File pomFile = cqVersionUnderTestFolder.resolve("pom.xml").toFile();

        try (FileReader fileReader = new FileReader(pomFile, StandardCharsets.UTF_8)) {
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model pomModel = reader.read(fileReader);

            pomModel.getParent().setVersion(cqVersion);

            if (cqStagingRepositoryUrl != null) {
                Repository cqStagingRepository = new Repository();
                cqStagingRepository.setId("camel-quarkus-staging");
                cqStagingRepository.setName("Camel Quarkus Staging Repository");
                cqStagingRepository.setUrl(cqStagingRepositoryUrl);
                pomModel.getPluginRepositories().add(cqStagingRepository);
                pomModel.getRepositories().add(cqStagingRepository);
            }
            if (camelStagingRepositoryUrl != null) {
                Repository camelStagingRepository = new Repository();
                camelStagingRepository.setId("camel-staging");
                camelStagingRepository.setName("Camel Staging Repository");
                camelStagingRepository.setUrl(camelStagingRepositoryUrl);
                pomModel.getPluginRepositories().add(camelStagingRepository);
                pomModel.getRepositories().add(camelStagingRepository);
            }

            try (FileWriter fileWriter = new FileWriter(pomFile, StandardCharsets.UTF_8)) {
                MavenXpp3Writer writer = new MavenXpp3Writer();
                writer.write(fileWriter, pomModel);
            }
        }
    }

}
