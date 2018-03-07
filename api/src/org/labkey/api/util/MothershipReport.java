/*
 * Copyright (c) 2006-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.util;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.reader.Readers;
import org.labkey.api.settings.AppProps;

import javax.mail.internet.ContentType;
import javax.net.ssl.HttpsURLConnection;
import javax.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A submission of data to the mothership running on labkey.org. Separate from the Mothership module, which
 * contains the code that receives the report.
 * User: jeckels
 * Date: Apr 20, 2006
 */
public class MothershipReport implements Runnable
{
    private final URL _url;
    private final Map<String, String> _params = new LinkedHashMap<>();
    private final String _errorCode;
    private int _responseCode = -1;
    private String _content;
    private final Target _target;
    private String _forwardedFor = null;
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
    public static final List<String> BORING_HOSTNAMES = Arrays.asList("localhost", "127.0.0.1");
    public static final String MOTHERSHIP_STATUS_HEADER_NAME = "MothershipStatus";
    public static final String MOTHERSHIP_STATUS_SUCCESS = "Success";
    public static final int ERROR_CODE_LENGTH = 6;

    public static final String CONTAINER_PATH = "/_mothership";
    public static final String BASE_URL = "/mothership" + CONTAINER_PATH;
    private static boolean showSelfReportExceptions = false;
    private static int _droppedExceptionCount = 0;

    /** @return true if this server can self-report exceptions (that is, has the Mothership module installed) */
    public static boolean isShowSelfReportExceptions()
    {
        return showSelfReportExceptions;
    }

    /** @param b whether this server can self-report exceptions.
     *           Default value is false, but the Mothership module will call this to set it to true if it's installed. */
    public static void setShowSelfReportExceptions(boolean b)
    {
        showSelfReportExceptions = b;
    }

    /**
     * Increment droppedExceptionCount when an exceptionReport submission is blocked by the RateLimiter in ExceptionUtil
     * We'll send this count in usage reports and monitor that we aren't being overly prescriptive with the RateLimiter
     * settings.
     */
    public static void incrementDroppedExceptionCount()
    {
        _droppedExceptionCount++;
    }

    public static int getDroppedExceptionCount()
    {
        return _droppedExceptionCount;
    }

    public enum Type
    {
        ReportException
        {
            @Override
            String getAction()
            {
                return "reportException";
            }

            @Override
            boolean includeErrorCode()
            {
                return true;
            }
        },
        CheckForUpdates
        {
            @Override
            String getAction()
            {
                return "checkForUpdates";
            }};

        URLHelper getURL() throws URISyntaxException
        {
            return new URLHelper(BASE_URL + "/" + getAction() + ".post");
        }

        abstract String getAction();
        boolean includeErrorCode()
        {
            return false;
        }
    }

    public static boolean isMothershipExceptionReport(String url)
    {
        return url.toLowerCase().contains((BASE_URL + "/" + Type.ReportException.getAction()).toLowerCase());
    }

    public enum Target
    {
        remote(false)
                {
                    @Override
                    URL getUrl(URLHelper urlHelper) throws MalformedURLException
                    {
                        urlHelper.setContextPath("/");
                        return new URL("https", "www.labkey.org", 443, urlHelper.toString());
                    }
                },
        local(true),
        test(true)
                {
                    @Override
                    String getHostName()
                    {
                        return "TEST_" + super.getHostName();
                    }
                };

        private final boolean _local;

        Target(boolean local)
        {
            _local = local;
        }

        public boolean isLocal()
        {
            return _local;
        }

        URL getUrl(URLHelper urlHelper) throws MalformedURLException
        {
            URL url;
            // Don't submit to the mothership server, go to the local machine
            try
            {
                urlHelper.setContextPath(AppProps.getInstance().getContextPath());
                url = new URL(AppProps.getInstance().getScheme(), "localhost", AppProps.getInstance().getServerPort(), urlHelper.toString());
            }
            catch (IllegalStateException e)
            {
                // Forget about local mothership report... we're probably bootstrapping the server
                url = null;
            }

            return url;
        }

        String getHostName()
        {
            try
            {
                return new URI(AppProps.getInstance().getBaseServerUrl()).getHost();
            }
            catch (URISyntaxException e)
            {
                return "localhost";
            }
        }
    }

    public MothershipReport(Type type, Target target, String errorCode) throws MalformedURLException, URISyntaxException
    {
        _target = target;
        _url = target.getUrl(type.getURL());

        if (type.includeErrorCode())
        {
            if (null == errorCode)
                _errorCode = RandomStringUtils.randomAlphanumeric(ERROR_CODE_LENGTH).toUpperCase();
            else
                _errorCode = errorCode;

            addParam("errorCode", _errorCode);
        }
        else
            _errorCode = null;
    }

    public int getResponseCode()
    {
        return _responseCode;
    }

    public void addParam(String key, long value)
    {
        addParam(key, Long.toString(value));
    }

    public void addParam(String key, boolean value)
    {
        addParam(key, Boolean.toString(value));
    }

    public void addParam(String key, String value)
    {
        if (_params.containsKey(key))
        {
            throw new IllegalArgumentException("This report already has a " + key + " parameter");
        }
        _params.put(key, value);
    }

    public Map<String, String> getParams()
    {
        return Collections.unmodifiableMap(_params);
    }

    public String getErrorCode()
    {
        return _errorCode;
    }

    /**
     * For local/TeamCity dev/test, allow spoofing the X-Forwarded-For header to simulate receiver being behind a load balancer.
     */
    public void setForwardedFor(String forwardedFor)
    {
        if (_target.isLocal())
            _forwardedFor = forwardedFor;
    }

    public void addHostName()
    {
        String hostName = _target.getHostName();
        if (!BORING_HOSTNAMES.contains(hostName))
            addParam("serverHostName", hostName);
    }

    public void run()
    {
        try
        {
            HttpURLConnection connection = openConnectionWithRedirects(_url, _forwardedFor);
            try
            {
                _responseCode = connection.getResponseCode();
                if (_responseCode == 200 && MOTHERSHIP_STATUS_SUCCESS.equals(connection.getHeaderField(MOTHERSHIP_STATUS_HEADER_NAME)))
                {
                    String encoding = StringUtilsLabKey.DEFAULT_CHARSET.name();

                    if (connection.getContentType() != null)
                    {
                        ContentType contentType = new ContentType(connection.getContentType());
                        encoding = contentType.getParameter("charset");
                    }

                    try (InputStream in = connection.getInputStream())
                    {
                        _content = IOUtils.toString(in, encoding);
                    }
                }
            }
            finally
            {
                connection.disconnect();
            }
        }
        catch (Exception ignored)
        {
            // Don't bother the client if this report fails
        }
    }

    private HttpURLConnection openConnectionWithRedirects(URL url, @Nullable String forwardedFor)
            throws IOException
    {
        boolean redirect;
        HttpURLConnection connection;
        int redirectCount = 0;
        do
        {
            redirect = false;
            connection = submitRequest(url, forwardedFor);
            int responseCode = connection.getResponseCode();
            if (responseCode >= 300 && responseCode <= 307 && responseCode != 306 && responseCode != HttpURLConnection.HTTP_NOT_MODIFIED)
            {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location != null)
                {
                    URL target = new URL(url, location);
                    if ((target.getProtocol().equals("http") || target.getProtocol().equals("https")) && redirectCount < 5)
                    {
                        redirect = true;
                        redirectCount++;
                        url = target;
                    }
                }
            }
        }
        while (redirect && redirectCount < 5);
        return connection;
    }

    private HttpURLConnection submitRequest(URL url, @Nullable String forwardedFor) throws IOException
    {
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        if (connection instanceof HttpsURLConnection)
        {
            HttpsUtil.disableValidation((HttpsURLConnection)connection);
        }
        // We'll handle redirects on our own which makes sure that we
        // POST instead of GET after being redirected
        connection.setInstanceFollowRedirects(false);
        connection.setDoOutput(true);
        connection.setDoInput(true);
        // For dev/testing on a local submission simulating a receiver behind a load balancer
        if (null != forwardedFor)
            connection.setRequestProperty(X_FORWARDED_FOR, forwardedFor);
        try (PrintWriter out = new PrintWriter(connection.getOutputStream(), true))
        {
            boolean first = true;
            for (Map.Entry<String, String> entry : _params.entrySet())
            {
                String value = entry.getValue();
                if (value != null)
                {
                    if (!first)
                    {
                        out.print("&");
                    }
                    first = false;
                    out.println(entry.getKey() + "=" + URLEncoder.encode(value, StringUtilsLabKey.DEFAULT_CHARSET.name()));
                }
            }
        }

        connection.connect();
        return connection;
    }

    public void addServerSessionParams()
    {
        Module coreModule = ModuleLoader.getInstance().getCoreModule();
        if (coreModule.getVcsRevision() != null)
        {
            addParam("svnRevision", coreModule.getVcsRevision());
        }
        if (!NumberUtils.isDigits(coreModule.getVcsRevision()))
        {
            addParam("description", "Core v" + coreModule.getFormattedVersion());
        }
        String svnURL = coreModule.getVcsUrl();
        if (svnURL != null)
        {
            addParam("svnURL", svnURL);
        }
        addParam("runtimeOS", System.getProperty("os.name"));
        addParam("javaVersion", System.getProperty("java.version"));
        addParam("enterprisePipelineEnabled", PipelineService.get() != null && PipelineService.get().isEnterprisePipeline());

        addParam("heapSize", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax() / 1024 / 1024);

        DbSchema schema = CoreSchema.getInstance().getSchema();
        if (schema != null)
        {
            DbScope scope = schema.getScope();
            addParam("databaseProductName", scope.getDatabaseProductName());
            addParam("databaseProductVersion", scope.getDatabaseProductVersion());
            addParam("databaseDriverName", scope.getDriverName());
            addParam("databaseDriverVersion", scope.getDriverVersion());
        }
        addParam("serverSessionGUID", AppProps.getInstance().getServerSessionGUID());
        addParam("serverGUID", AppProps.getInstance().getServerGUID());

        ServletContext context = ModuleLoader.getServletContext();
        String servletContainer = context == null ? null : context.getServerInfo();
        addParam("servletContainer", servletContainer);
        addParam("usedInstaller", usedInstaller());
        addParam("distribution", getDistributionStamp());
        addParam("usageReportingLevel", AppProps.getInstance().getUsageReportingLevel().toString());
        addParam("exceptionReportingLevel", AppProps.getInstance().getExceptionReportingLevel().toString());
    }

    public String getContent()
    {
        return _content;
    }

    public static boolean usedInstaller()
    {
        ServletContext context = ModuleLoader.getServletContext();
        String usedInstaller = context == null ? null : context.getInitParameter("org.labkey.api.util.mothershipreport.usedInstaller");

        return Boolean.parseBoolean(usedInstaller);
    }

    private static String getDistributionStamp()
    {
        String distributionStamp;
        try(InputStream input = MothershipReport.class.getResourceAsStream("/distribution"))
        {
            if (null != input)
            {
                distributionStamp = Readers.getReader(input).lines().collect(Collectors.joining("\n"));
                if (StringUtils.isEmpty(distributionStamp))
                {
                    distributionStamp = "Distribution File Empty";
                }
            }
            else
            {
                distributionStamp = "Distribution File Missing";
            }
        }
        catch (IOException e)
        {
            distributionStamp = "Exception reading distribution file. " + e.getMessage();
        }

        return distributionStamp;
    }
}
