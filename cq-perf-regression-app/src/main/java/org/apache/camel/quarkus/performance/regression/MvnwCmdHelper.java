package org.apache.camel.quarkus.performance.regression;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.io.output.TeeOutputStream;

public class MvnwCmdHelper {

    public static String execute(Path cqVersionUnderTestFolder, String args) {

        ByteArrayOutputStream stdoutMemoryStream = null;
        FileOutputStream stdoutFileStream = null;
        TeeOutputStream teeOutputStream = null;

        try {
            File mvnwFile = cqVersionUnderTestFolder.resolve("mvnw").toFile();
            CommandLine cmd = CommandLine.parse(mvnwFile.getAbsolutePath() + " " + args);

            stdoutMemoryStream = new ByteArrayOutputStream();
            File logFile = cqVersionUnderTestFolder.resolve("logs.txt").toFile();
            stdoutFileStream = new FileOutputStream(logFile, true);

            stdoutFileStream.write("\n\n**********************************************************************\n".getBytes(StandardCharsets.UTF_8));
            stdoutFileStream.write("**********************************************************************\n".getBytes(StandardCharsets.UTF_8));
            stdoutFileStream.write(("** "+cmd+"\n").getBytes(StandardCharsets.UTF_8));
            stdoutFileStream.write("**********************************************************************\n".getBytes(StandardCharsets.UTF_8));
            stdoutFileStream.write("**********************************************************************\n".getBytes(StandardCharsets.UTF_8));

            teeOutputStream = new TeeOutputStream(stdoutMemoryStream, stdoutFileStream);
            DefaultExecutor executor = new DefaultExecutor();
            PumpStreamHandler psh = new PumpStreamHandler(teeOutputStream);
            executor.setStreamHandler(psh);
            executor.setWorkingDirectory(cqVersionUnderTestFolder.toFile());

            int exitValue = executor.execute(cmd);
            String outAndErr = stdoutMemoryStream.toString(StandardCharsets.UTF_8);
            if (exitValue != 0) {
                throw new RuntimeException("The command '" + cmd + "' has returned exitValue " + exitValue + ", process logs below:\n" + outAndErr);
            }

            return outAndErr;
        } catch (IOException ex) {
            throw new RuntimeException("An issue occurred while attempting to execute 'mvnw " + args + "', more logs may be found in " + cqVersionUnderTestFolder + "/logs.txt if exists", ex);
        } finally {
            IOUtils.closeQuietly(stdoutMemoryStream);
            IOUtils.closeQuietly(stdoutFileStream);
            IOUtils.closeQuietly(teeOutputStream);
        }
    }

}
