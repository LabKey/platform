package org.labkey.api.reports.report.python;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.impl.common.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.ApiModule;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.premium.PremiumService;
import org.labkey.api.reports.ExternalScriptEngine;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.reports.report.DockerScriptReport;
import org.labkey.api.reports.report.ScriptEngineReport;
import org.labkey.api.reports.report.ScriptReportDescriptor;
import org.labkey.api.reports.report.r.view.ConsoleOutput;
import org.labkey.api.reports.report.r.view.IpynbOutput;
import org.labkey.api.security.SessionApiKeyManager;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspTemplate;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.PrintWriters;
import org.labkey.vfs.FileLike;
import org.springframework.validation.BindException;

import javax.script.ScriptEngine;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.cl;

public class IpynbReport extends DockerScriptReport
{
    static final Logger LOG = LogHelper.getLogger(IpynbReport.class, "Ipynb Report");

    public static final String TYPE = "ReportService.ipynbReport";
    public static final String CONFIG_FILE = "report_config.json";
    public static final String ERROR_OUTPUT = "errors.txt";
    public static final String LABEL = "Jupyter Report";
    public static final String EXTENSION = "ipynb";

    
    public record Env(String env, String header) {}
    public static final Env LABKEY_USERID = new Env("LABKEY_USERID", "X-LABKEY-USERID");
    public static final Env LABKEY_EMAIL = new Env("LABKEY_EMAIL", "X-LABKEY-EMAIL");
    public static final Env LABKEY_APIKEY = new Env( "LABKEY_APIKEY", "X-LABKEY-APIKEY");
    public static final Env LABKEY_CSRF = new Env( "LABKEY_CSRF", "X-LABKEY-CSRF");


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
        if (PremiumService.get().isEnabled() && ModuleLoader.getInstance().hasModule("professional"))
        {
            LabKeyScriptEngineManager mgr = LabKeyScriptEngineManager.get();
            List<ExternalScriptEngineDefinition> defs = mgr.getEngineDefinitions(ExternalScriptEngineDefinition.Type.Jupyter);

            // we currently only support a single site scoped engine
            if (defs.size() == 1 && Arrays.asList(defs.get(0).getExtensions()).contains(EXTENSION))
                return defs.get(0).isEnabled();
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
                      ],
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
        FileLike workingDirectory = getReportDir(context.getContainer().getId());

        if (!workingDirectory.isDirectory())
            throw new IOException("Could not create working directory");
        FileUtil.deleteDirectoryContents(workingDirectory);

        // write the script out to the working directory
        var descriptor = getDescriptor();
        String script = descriptor.getProperty(ScriptReportDescriptor.Prop.script);
        FileLike scriptFile = workingDirectory.resolveChild(FileUtil.makeLegalName(descriptor.getReportName()) + ".ipynb");
        FileUtil.createTempFile(scriptFile);
        IOUtil.copyCompletely(new StringReader(script), PrintWriters.getPrintWriter(scriptFile.openOutputStream()));

        logFiles(workingDirectory, "BEFORE");

        ExecuteStrategy ex = new WebServiceExecuteStrategy();

        int exitCode = ex.execute(context, apikey, workingDirectory, scriptFile);
        LOG.trace("EXIT: " + exitCode);
        FileLike outputFile = ex.getOutputDocument();
        LOG.trace("OUTPUT: " + outputFile);
        logFiles(workingDirectory, "AFTER");

        try
        {
            VBox vbox = new VBox();

            if (exitCode != 0)
            {
                vbox.addView(new HtmlView(DIV(cl("labkey-error"), "Process exited with non-zero code: " + exitCode + ".")));
            }
            if (outputFile == null)
            {
                vbox.addView(new HtmlView(DIV(cl("labkey-error"), "No document was generated.")));
            }
            else
            {
                BasicFileAttributes outputFileAttributes = Files.readAttributes(outputFile.toNioPathForRead(), BasicFileAttributes.class);
                if (outputFileAttributes.isRegularFile() && 0 < outputFileAttributes.size())
                {
                    vbox.addView(new IpynbOutput(outputFile).getView(context));
                }
                else
                {
                    vbox.addView(new HtmlView(DIV(cl("labkey-error"), "Unable to process report output.")));
                }
            }

            // if there is console.txt or errors.txt file render them
            FileLike console = workingDirectory.resolveChild(ScriptEngineReport.CONSOLE_OUTPUT);
            if (console.isFile() && console.getSize() > 0)
                vbox.addView(new ConsoleOutput(console).getView(context));

            FileLike error = workingDirectory.resolveChild(ERROR_OUTPUT);
            if (error.isFile() && error.getSize() > 0)
                vbox.addView(new ConsoleOutput(error).getView(context));

            LOG.trace("VIEWS: " + vbox.getViews().size());
            return vbox;
        }
        catch (Exception x)
        {
            LOG.error("Error rendering report", x);
            throw x;
        }
    }

    private static void logFiles(FileLike parentDir, String label)
    {
        File dir = parentDir.toNioPathForRead().toFile();
        Collection<File> files = FileUtils.listFiles(dir, null, true);
        LOG.trace(label + ": " + dir.getPath() + "\n\t" +
                StringUtils.join(files.stream().map(f ->
                        f.getPath().replace(dir.getPath(), "") + " : " + f.length()).toArray(), "\n\t"));
    }


    @Override
    protected JSONObject createReportConfig(ViewContext context, FileLike scriptFile)
    {
        return super.createReportConfig(context, scriptFile);
    }


    @NotNull
    URL getServiceAddress(Container c) throws ConfigurationException
    {
        ScriptEngine eng = LabKeyScriptEngineManager.get().getEngineByExtension(c, EXTENSION);
        String urlString = null;
        if (eng instanceof ExternalScriptEngine engine)
        {
            try
            {
                urlString = engine.getEngineDefinition().getRemoteUrl();
                if (isNotBlank(urlString))
                    return new URL(urlString);
                throw new MalformedURLException("URL is empty");
            }
            catch (MalformedURLException e)
            {
                throw new ConfigurationException("Bad service endpoint: " + urlString, e);
            }
        }
        else
        {
            throw new IllegalStateException("No script engine configured for  " + LABEL + " reports");
        }
    }


    private static void extractTar(InputStream in, FileLike targetDirectory) throws IOException
    {
        try (TarArchiveInputStream tar = new TarArchiveInputStream(in))
        {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null)
            {
                FileLike path = targetDirectory.resolveFile(org.labkey.api.util.Path.parse(entry.getName()));
                if (entry.isDirectory())
                {
                    FileUtil.mkdir(path);
                }
                else
                {
                    FileUtil.createTempFile(path);
                    try (OutputStream os = path.openOutputStream())
                    {
                        IOUtils.copy(tar, os);
                    }
                }
            }
        }
    }


    /**
     *  ExecuteStrategy is only concerned with invoking the external process.
     *  It should not care about report artifacts/outputs, except for copying them into the working directory.
     */

    private interface ExecuteStrategy
    {
        IpynbReport getReport();
        int execute(ViewContext context, String apiKey, FileLike working, FileLike ipynb) throws IOException;

        // document could be .html .ipynb or .md
        @Nullable FileLike getOutputDocument();
    }


    private static boolean successfulPing = false;


    class WebServiceExecuteStrategy implements ExecuteStrategy
    {
        FileLike inputScript;
        FileLike outputDocument;

        @Override
        public IpynbReport getReport()
        {
            return null;
        }

        /* give webservice chance to startup */
        private void tryPing(URL service)
        {
            for (int retry=0 ; !successfulPing && retry < 5 ; retry++)
            {
                var res = testServiceEndpoint(service);
                if (200 == res.statusCode)
                    successfulPing = true;
            }
        }


        @Override
        public int execute(ViewContext context, String apiKey, FileLike working, FileLike ipynb) throws IOException
        {
            inputScript = ipynb;

            JSONObject reportConfig = createReportConfig(context, ipynb);

            // I tried "putting" a fake tar entry, but TarArchiveOutputStream seems to actually want the file to exist
            try (OutputStream configOut = working.resolveChild(CONFIG_FILE).openOutputStream();
                PrintWriter writer = PrintWriters.getPrintWriter(configOut))
            {
                writer.print(reportConfig.toString());
            }

            URL service = getServiceAddress(context.getContainer());
            // For testing, just return if the remoteURL host is "noop.test"
            if ("noop.test".equals(service.getHost()))
                return 0;

            tryPing(service);

            SimpleMetricsService.get().increment(ModuleLoader.getInstance().getModule(ApiModule.class).getName(), ScriptEngineReport.METRIC_FEATURE_AREA, getClass().getSimpleName());

            try (CloseableHttpClient client = HttpClients.createDefault())
            {
                HttpPut putRequest = new HttpPut(new URLHelper(service.toString()).setPath("/evaluate").getURIString());

                putRequest.setHeader(LABKEY_APIKEY.header(), apiKey);
                putRequest.setHeader(LABKEY_USERID.header(), String.valueOf(context.getUser().getUserId()));
                putRequest.setHeader(LABKEY_EMAIL.header(), context.getUser().getEmail());
                if (null != context.getRequest())
                    putRequest.setHeader(LABKEY_CSRF.header(), CSRFUtil.getExpectedToken(context.getRequest(), null));

                final PipedInputStream in = new PipedInputStream();
                final PipedOutputStream pipeOutput = new PipedOutputStream();
                pipeOutput.connect(in);

                final InputStreamEntity entity = new InputStreamEntity(in, ContentType.create("application/x-tar"));
                putRequest.setEntity(entity);

                final DbScope.RetryPassthroughException[] bgException = new DbScope.RetryPassthroughException[1];
                final Thread t = new Thread(() -> {
                    try (TarArchiveOutputStream tar = new TarArchiveOutputStream(pipeOutput))
                    {
                        List<FileLike> files = working.getChildren();
                        for (var file : files)
                        {
                            TarArchiveEntry entry = tar.createArchiveEntry(file.toNioPathForRead(), file.getName());
                            tar.putArchiveEntry(entry);
                            try(InputStream is = file.openInputStream())
                            {
                                IOUtils.copy(is, tar);
                            }
                            tar.closeArchiveEntry();
                        }
                    }
                    catch (IOException ex)
                    {
                        bgException[0] = new DbScope.RetryPassthroughException(ex);
                    }
                });
                t.start();
                try (CloseableHttpResponse response = client.execute(putRequest))
                {
                    try
                    {
                        t.join(5000);
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
                    ipynb.delete();

                    if (200 != response.getCode())
                        return response.getCode();
                    extractTar(response.getEntity().getContent(), working);
                    return 0;
                }
            }
            catch (URISyntaxException x)
            {
                throw new ConfigurationException("Error in jupyter endpoint configuration: " + service, x);
            }
        }

        @Override
        public @Nullable FileLike getOutputDocument()
        {
            if (null != outputDocument && outputDocument.isFile())
                return outputDocument;
            if (null != inputScript && inputScript.isFile())
                return inputScript;
            return null;
        }
    }


    public record PingResult (int statusCode, String message) {}

    public static PingResult testServiceEndpoint(URL service)
    {
        try (CloseableHttpClient client = HttpClients.createDefault())
        {
            HttpGet getRequest = new HttpGet(new URLHelper(service.toString()).setPath("/ping").getURIString());
            try (CloseableHttpResponse response = client.execute(getRequest))
            {
                return new PingResult(response.getCode(),"");
            }
            catch (Exception x)
            {
                return new PingResult(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, x.getMessage());
            }
        }
        catch (URISyntaxException|IOException x)
        {
            return new PingResult(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, x.getMessage());
        }
    }
}
