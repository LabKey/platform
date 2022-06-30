package org.labkey.api.reports.report.python;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.impl.common.IOUtil;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.docker.DockerService;
import org.labkey.api.reports.ExternalScriptEngine;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.reports.report.DockerScriptReport;
import org.labkey.api.reports.report.ScriptEngineReport;
import org.labkey.api.reports.report.ScriptReportDescriptor;
import org.labkey.api.reports.report.r.view.ConsoleOutput;
import org.labkey.api.reports.report.r.view.IpynbOutput;
import org.labkey.api.security.SessionApiKeyManager;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspTemplate;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import javax.script.ScriptEngine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.cl;

public class IpynbReport extends DockerScriptReport
{
    static final Logger LOG = LogHelper.getLogger(IpynbReport.class, "Ipynb Report");

    public static final String TYPE = "ReportService.ipynbReport";
    public static final String ERROR_OUTPUT = "errors.txt";
    public static final String LABEL = "Jupyter Report";
    public static final String EXTENSION = "ipynb";

    public IpynbReport()
    {
        this(TYPE, IpynbReportDescriptor.DESCRIPTOR_TYPE);
    }

    IpynbReport(String reportType, String defaultDescriptorType)
    {
        super(reportType, defaultDescriptorType);
    }

    @Override
    public Pair<String, String> startExternalEditor(ViewContext context, String script, BindException errors)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType()
    {
        return IpynbReport.TYPE;
    }

    @Override
    public String getTypeDescription()
    {
        return "Jupyter Report (.ipynb)";
    }

    public static boolean isEnabled()
    {
        if (DockerService.get() != null)
        {
            return LabKeyScriptEngineManager.get().getEngineDefinitions(ExternalScriptEngineDefinition.Type.Docker).stream()
                    .anyMatch(def -> Arrays.asList(def.getExtensions()).contains(EXTENSION));
        }
        return false;
    }

    @Nullable
    @Override
    public String getEditAreaSyntax()
    {
        return "application/ld+json";
    }

    @Override
    public String getDefaultScript()
    {
        return """
                {
                  "cells": [
                    {
                      "cell_type": "code",
                      "source": [
                        "from ReportConfig import get_report_api_wrapper, get_report_data, get_report_parameters\\n",
                        "print(get_report_parameters())\\n",
                        "print(get_report_data())"
                      ]
                      "metadata": {},
                      "execution_count": null,
                      "outputs": []
                    }
                  ],
                  "metadata": {},
                  "nbformat": 4,
                  "nbformat_minor": 5
                }""";
    }

    @Override
    public @Nullable String getDesignerHelpHtml()
    {
        try
        {
            return new JspTemplate<>("/org/labkey/api/reports/report/view/ipynbReportHelp.jsp").render();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public HttpView<?> renderReport(ViewContext context) throws Exception
    {
        if (context.getRequest() == null)
            throw new IllegalStateException("Invalid report context");
        String apikey = SessionApiKeyManager.get().getApiKey(context.getRequest(), "ipynb report");
        File workingDirectory = getReportDir(context.getContainer().getId());

        assert workingDirectory.isAbsolute();
        if (!workingDirectory.isDirectory())
            throw new IOException("Could not create working directory");
        FileUtils.cleanDirectory(workingDirectory);

        // write the script out to the working directory
        var descriptor = getDescriptor();
        String script = descriptor.getProperty(ScriptReportDescriptor.Prop.script);
        File scriptFile = new File(workingDirectory,FileUtil.makeLegalName(descriptor.getReportName()) + ".ipynb");
        IOUtil.copyCompletely(new StringReader(script), new FileWriter(scriptFile, StringUtilsLabKey.DEFAULT_CHARSET));

        Set<File> beforeExecute = new HashSet<>(FileUtils.listFiles(workingDirectory, null, true));
        LOG.trace("BEFORE: " + StringUtils.join(beforeExecute.stream().map(File::getName).toArray(), "\n\t"));

        ExecuteStrategy ex = new DockerRunTarStdinStdout();
        int exitCode = ex.execute(context, apikey, workingDirectory, scriptFile);
        File outputFile = ex.getOutputDocument();

        Set<File> afterExecute = new HashSet<>(FileUtils.listFiles(workingDirectory, null, true));
        LOG.trace("wd:    " + workingDirectory.getPath());
        LOG.trace("AFTER: " + StringUtils.join(afterExecute.stream().map(File::getName).toArray(), "\n\t"));

        try
        {
            VBox vbox = new VBox();

            if (exitCode != 0)
            {
                vbox.addView(new HtmlView(DIV(cl("labkey-error"), "Process exited with non-zero code: " + exitCode + ".")));
            }
            else if (outputFile == null)
            {
                vbox.addView(new HtmlView(DIV(cl("labkey-error"), "No document was generated.")));
            }
            else
            {
                BasicFileAttributes outputFileAttributes = Files.readAttributes(outputFile.toPath(), BasicFileAttributes.class);
                if (outputFileAttributes.isRegularFile() && 0 < outputFileAttributes.size())
                {
                    vbox.addView(new IpynbOutput(outputFile).getView(context));
                }
            }

            // if there is console.txt or errors.txt file render them
            File console = new File(workingDirectory, ScriptEngineReport.CONSOLE_OUTPUT);
            if (console.isFile() && console.length() > 0)
                vbox.addView(new ConsoleOutput(console).getView(context));

            File error = new File(workingDirectory, ERROR_OUTPUT);
            if (error.isFile() && error.length() > 0)
                vbox.addView(new ConsoleOutput(error).getView(context));

            return vbox;
        }
        catch (Exception x)
        {
            x.printStackTrace();
            throw x;
        }
    }


    @Override
    protected JSONObject createReportConfig(ViewContext context, File scriptFile)
    {
        return super.createReportConfig(context, scriptFile);
    }


    /**
     *  ExecuteStrategy is only concerned with invoking the external process.
     *  It should not care about report artifacts/outputs, except for copying them into the working directory.
     */

    private interface ExecuteStrategy
    {
        IpynbReport getReport();
        int execute(ViewContext context, String apiKey, File working, File ipynb) throws IOException;

        // document could be .html .ipynb or .md
        @Nullable File getOutputDocument();
    }


//    final StringBuilder stdout = new StringBuilder();
//    final StringBuilder stderr = new StringBuilder();
//   new Thread(() -> Readers.getReader(process.getInputStream()).lines().forEach(stdout::append)).start();
//   new Thread(() -> Readers.getReader(process.getErrorStream()).lines().forEach(stderr::append)).start();
//

    // process that writes the computed ipynb file to stdout
    class ExecIpynbStdout implements ExecuteStrategy
    {
        final String[] shellCommand;
        File outputDocument;

        @Override
        public IpynbReport getReport()
        {
            return IpynbReport.this;
        }

        ExecIpynbStdout(String[] shellCommand)
        {
            this.shellCommand = shellCommand;
        }

        @Override
        public int execute(ViewContext context, String apiKey, File working, File ipynb) throws IOException
        {
            String name = FileUtil.getBaseName(ipynb);
            String outputName = name + ".nbconvert.ipynb";
            String ext = FileUtil.getExtension(ipynb);
            if ("ipynb".equalsIgnoreCase(ext))
                outputName = name + ".nbconvert." + ext;
            outputDocument = new File(working, outputName);

            String[] command = shellCommand.clone();
            boolean foundSubst = false;
            for (int i = 0; i < command.length; i++)
            {
                if ("{}".equals(command[i]))
                {
                    command[i] = ipynb.getAbsolutePath();
                    foundSubst = true;
                }
            }

            try
            {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(working);
                pb.redirectOutput(outputDocument);
                pb.redirectError(new File(working, ERROR_OUTPUT));
                if (!foundSubst)
                    pb.redirectInput(ipynb);
                final Process process = pb.start();
                return process.waitFor();
            }
            catch (InterruptedException iex)
            {
                throw new IOException(iex);
            }
        }


        @Override
        public File getOutputDocument()
        {
            if (outputDocument.isFile())
                return outputDocument;
            return null;
        }
    }

    DockerService.ImageConfig getImageConfig(Container c)
    {
        // apply configuration options from the configured docker engine
        ScriptEngine eng = LabKeyScriptEngineManager.get().getEngineByExtension(c, EXTENSION);
        if (eng instanceof ExternalScriptEngine engine)
        {
            // this is a little strange, DockerImage and ImageConfig classes overlap quite a bit, I'm not sure
            // why they aren't more interchangeable.
            DockerService.DockerImage image = DockerService.get().getDockerImage(engine.getEngineDefinition().getDockerImageRowId());
            // todo : are there other configuration options we need to propagate
            return DockerService.get().getImageConfigBuilder(image.getImageName()).build();
        }
        else
        {
            throw new IllegalStateException("No script engine configured for  " + LABEL + " reports");
        }
    }


    class DockerRunIpynbStdinStdout implements ExecuteStrategy
    {
        File inputScript;
        File outputDocument;

        @Override
        public IpynbReport getReport()
        {
            return IpynbReport.this;
        }

        DockerRunIpynbStdinStdout()
        {
        }

        @Override
        public int execute(ViewContext context, String apiKey, File working, File ipynb) throws IOException
        {
            String name = FileUtil.getBaseName(ipynb);
            String outputName = name + ".nbconvert.ipynb";
            String ext = FileUtil.getExtension(ipynb);
            if ("ipynb".equalsIgnoreCase(ext))
                outputName = name + ".nbconvert." + ext;
            outputDocument = new File(working, outputName);
            inputScript = ipynb;

            DockerService.ImageConfig image = getImageConfig(context.getContainer());

            final PipedInputStream in = new PipedInputStream();
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();

            // DockerService.run() blocks until process completion, so input stream has to be written on a different threads
            final DbScope.RetryPassthroughException[] bgException = new DbScope.RetryPassthroughException[1];
            Thread t = new Thread(() -> {
                try (PipedOutputStream pipeOutput = new PipedOutputStream();
                    var fis = new FileInputStream(ipynb))
                {
                    pipeOutput.connect(in);
                    IOUtils.copy(fis, pipeOutput);
                }
                catch (IOException ex)
                {
                    bgException[0] = new DbScope.RetryPassthroughException(ex);
                }
            });
            t.start();

            String tempDir = "/tmp/" + GUID.makeGUID();
            var environment = Map.of(
                    "TEMP_DIRECTORY", tempDir,
                    "LABKEY_API_KEY", apiKey);
            try (var run = DockerService.get().run(image, "ipynb", environment, in, out, err))
            {
                t.interrupt();
                try
                {
                    t.join(1000);
                }
                catch (InterruptedException x)
                {
                    // pass
                }
                if (null != bgException[0])
                {
                    bgException[0].rethrow(IOException.class);
                    bgException[0].throwRuntimeException();
                }
                try (FileOutputStream fos = new FileOutputStream(outputDocument))
                {
                    out.writeTo(fos);
                }
                return run.getExitCode();
            }
            catch (Exception x)
            {
                throw UnexpectedException.wrap(x);
            }
        }


        @Override
        public File getOutputDocument()
        {
            if (null != outputDocument && outputDocument.isFile())
                return outputDocument;
// TODO UNDONE TODO
            if (null != inputScript && inputScript.isFile())
                return inputScript;
            return null;
        }
    }


    class DockerRunTarStdinStdout implements ExecuteStrategy
    {
        File inputScript;
        File outputDocument;

        @Override
        public IpynbReport getReport()
        {
            return IpynbReport.this;
        }

        DockerRunTarStdinStdout()
        {
        }

        @Override
        public int execute(ViewContext context, String apiKey, File working, File ipynb) throws IOException
        {
            inputScript = ipynb;

            JSONObject reportConfig = createReportConfig(context, ipynb);
            // I tried "putting" a fake tar entry, but TarArchiveOutputStream seems to actually want the file to exist
            FileUtils.write(new File(working,"report_config.json"), reportConfig.toString(), StringUtilsLabKey.DEFAULT_CHARSET);

            File[] listFiles = working.listFiles();
            List<File> files = null == listFiles ? List.of() : Arrays.asList(listFiles);
            DockerService.ImageConfig image = getImageConfig(context.getContainer());

            final PipedInputStream in = new PipedInputStream();
            // TODO would be nice to have a binary/OutputStream version of FileUtil.TempTextFileWrapper()
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();

            // DockerService.run() blocks until process completion, so input stream has to be written on a different threads
            final DbScope.RetryPassthroughException[] bgException = new DbScope.RetryPassthroughException[1];
            final Thread t = new Thread(() -> {
                try (
                    PipedOutputStream pipeOutput = new PipedOutputStream();
                    TarArchiveOutputStream tar = new TarArchiveOutputStream(pipeOutput)
                )
                {
                    pipeOutput.connect(in);
                    for (var file : files)
                    {
                        ArchiveEntry entry = tar.createArchiveEntry(file, file.getName());
                        tar.putArchiveEntry(entry);
                        IOUtils.copy(new FileInputStream(file), tar);
                        tar.closeArchiveEntry();
                    }
                }
                catch (IOException ex)
                {
                    bgException[0] = new DbScope.RetryPassthroughException(ex);
                }
            });
            t.start();

            String tempDir = "/tmp/" + GUID.makeGUID();
            var environment = Map.of(
                    "TEMP_DIRECTORY", tempDir,
                    "LABKEY_API_KEY", apiKey);
            try (var run = DockerService.get().run(image, "ipynb", environment, in, out, err))
            {
                try
                {
                    t.join(1000);
                }
                catch (InterruptedException x)
                {
                    // pass
                }
                if (null != bgException[0])
                {
                    bgException[0].rethrow(IOException.class);
                    bgException[0].throwRuntimeException();
                }

                // delete script to avoid returning unprocessed ipynb in case of error
                FileUtils.delete(ipynb);
                // TODO use PipedOutputStream to save to disk as we go instead of using ByteArrayOutputStream
                extractTar(new ByteArrayInputStream(out.toByteArray()), working);

                return run.getExitCode();
            }
            catch (Exception x)
            {
                throw UnexpectedException.wrap(x);
            }
        }

        private static void extractTar(InputStream in, File targetDirectory) throws IOException
        {
            try (TarArchiveInputStream tar = new TarArchiveInputStream(in))
            {
                TarArchiveEntry entry;
                while ((entry = (TarArchiveEntry) tar.getNextEntry()) != null)
                {
                    File path = new File(targetDirectory, entry.getName());
                    if (entry.isDirectory())
                    {
                        FileUtils.forceMkdir(path);
                    }
                    else
                    {
                        try (FileOutputStream os = new FileOutputStream(path))
                        {
                             IOUtils.copy(tar, os);
                        }
                    }
                }
            }
        }

        @Override
        public File getOutputDocument()
        {
            if (null != outputDocument && outputDocument.isFile())
                return outputDocument;
            if (null != inputScript && inputScript.isFile())
                return inputScript;
            return null;
        }
    }
}
