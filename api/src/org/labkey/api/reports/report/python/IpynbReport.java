package org.labkey.api.reports.report.python;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.impl.common.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
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
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspTemplate;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import javax.script.ScriptEngine;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    
    record Env(String env, String header) {}
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
        if (PremiumService.get().isEnabled())
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
        File workingDirectory = getReportDir(context.getContainer().getId());

        assert workingDirectory.isAbsolute();
        if (!workingDirectory.isDirectory())
            throw new IOException("Could not create working directory");
        FileUtils.cleanDirectory(workingDirectory);

        // write the script out to the working directory
        var descriptor = getDescriptor();
        String script = descriptor.getProperty(ScriptReportDescriptor.Prop.script);
        File scriptFile = new File(workingDirectory, FileUtil.makeLegalName(descriptor.getReportName()) + ".ipynb");
        IOUtil.copyCompletely(new StringReader(script), new FileWriter(scriptFile, StringUtilsLabKey.DEFAULT_CHARSET));

        Set<File> beforeExecute = new HashSet<>(FileUtils.listFiles(workingDirectory, null, true));
        LOG.trace("BEFORE: " + workingDirectory.getPath() + "\n\t" +
                StringUtils.join(beforeExecute.stream().map(f ->
                        f.getPath().replace(workingDirectory.toString(), "") + " : " + f.length()).toArray(), "\n\t"));

        ExecuteStrategy ex = new WebServiceExecuteStrategy();

        int exitCode = ex.execute(context, apikey, workingDirectory, scriptFile);
        LOG.trace("EXIT: " + exitCode);
        File outputFile = ex.getOutputDocument();
        LOG.trace("OUTPUT: " + outputFile);

        Set<File> afterExecute = new HashSet<>(FileUtils.listFiles(workingDirectory, null, true));
        LOG.trace("AFTER: " + workingDirectory.getPath() + "\n\t" +
                StringUtils.join(afterExecute.stream().map(f ->
                        f.getPath().replace(workingDirectory.toString(), "") + " : " + f.length()).toArray(), "\n\t"));

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
                BasicFileAttributes outputFileAttributes = Files.readAttributes(outputFile.toPath(), BasicFileAttributes.class);
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
            File console = new File(workingDirectory, ScriptEngineReport.CONSOLE_OUTPUT);
            if (console.isFile() && console.length() > 0)
                vbox.addView(new ConsoleOutput(console).getView(context));

            File error = new File(workingDirectory, ERROR_OUTPUT);
            if (error.isFile() && error.length() > 0)
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


    @Override
    protected JSONObject createReportConfig(ViewContext context, File scriptFile)
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
                if (urlString != null)
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


    private static boolean successfulPing = false;


    class WebServiceExecuteStrategy implements ExecuteStrategy
    {
        File inputScript;
        File outputDocument;

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
        public int execute(ViewContext context, String apiKey, File working, File ipynb) throws IOException
        {
            inputScript = ipynb;

            JSONObject reportConfig = createReportConfig(context, ipynb);

            // I tried "putting" a fake tar entry, but TarArchiveOutputStream seems to actually want the file to exist
            FileUtils.write(new File(working, CONFIG_FILE), reportConfig.toString(), StringUtilsLabKey.DEFAULT_CHARSET);

            URL service = getServiceAddress(context.getContainer());
            // For testing, just return if the remoteURL host is "noop.test"
            if ("noop.test".equals(service.getHost()))
                return 0;

            tryPing(service);

            try (CloseableHttpClient client = HttpClients.createDefault())
            {
                HttpPut putRequest = new HttpPut(new URLHelper(service.toString()).setPath("/evaluate").getURIString());

                putRequest.setHeader(LABKEY_APIKEY.header(), apiKey);
                putRequest.setHeader(LABKEY_USERID.header(), String.valueOf(context.getUser().getUserId()));
                putRequest.setHeader(LABKEY_EMAIL.header(), context.getUser().getEmail());
                if (null != context.getRequest())
                    putRequest.setHeader(LABKEY_CSRF.header(), CSRFUtil.getExpectedToken(context.getRequest(), null));

                final PipedInputStream in = new PipedInputStream();
                final InputStreamEntity entity = new InputStreamEntity(in);
                putRequest.setEntity(entity);

                final DbScope.RetryPassthroughException[] bgException = new DbScope.RetryPassthroughException[1];
                final Thread t = new Thread(() -> {
                    try (
                            PipedOutputStream pipeOutput = new PipedOutputStream();
                            TarArchiveOutputStream tar = new TarArchiveOutputStream(pipeOutput)
                    )
                    {
                        pipeOutput.connect(in);

                        File[] listFiles = working.listFiles();
                        List<File> files = null == listFiles ? List.of() : Arrays.asList(listFiles);
                        for (var file : files)
                        {
                            ArchiveEntry entry = tar.createArchiveEntry(file, file.getName());
                            tar.putArchiveEntry(entry);
                            try(FileInputStream fis = new FileInputStream(file))
                            {
                                IOUtils.copy(fis, tar);
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
                    FileUtils.delete(ipynb);

                    if (200 != response.getStatusLine().getStatusCode())
                        return response.getStatusLine().getStatusCode();
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
        public @Nullable File getOutputDocument()
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
                return new PingResult(response.getStatusLine().getStatusCode(),"");
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
