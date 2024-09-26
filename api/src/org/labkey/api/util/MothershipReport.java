/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.mail.internet.ContentType;
import jakarta.servlet.ServletContext;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.util.logging.LogHelper;

import javax.net.ssl.HttpsURLConnection;
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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A submission of data to the mothership running on labkey.org. Separate from the Mothership module, which
 * contains the code that receives the report.
 */
public class MothershipReport implements Runnable
{
    private final static Logger LOG = LogHelper.getLogger(MothershipReport.class, "Exception reporting and usage statistics submissions");
    private final URL _url;
    private final Map<String, String> _params = new LinkedHashMap<>();
    private final String _errorCode;
    private int _responseCode = -1;
    private String _upgradeMessage;
    private String _marketingUpdate;
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

    public final static String JSON_METRICS_KEY = "jsonMetrics";
    public static final String EXPERIMENTAL_LOCAL_MARKETING_UPDATE = "localMarketingUpdates";
    private static boolean _selfTestMarketingUpdates = OptionalFeatureService.get().isFeatureEnabled(EXPERIMENTAL_LOCAL_MARKETING_UPDATE);

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
            }
        };

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
                String baseServerUrl = AppProps.getInstance().getBaseServerUrl();
                if (baseServerUrl != null)
                {
                    return new URI(baseServerUrl).getHost();
                }
            }
            catch (URISyntaxException ignored) {}
            return "localhost";
        }
    }

    public MothershipReport(Type type, Target target, @Nullable String errorCode) throws MalformedURLException, URISyntaxException
    {
        _target = target;
        _url = target.getUrl(type.getURL());

        if (type.includeErrorCode())
        {
            _errorCode = Objects.requireNonNullElseGet(errorCode, () -> RandomStringUtils.randomAlphanumeric(ERROR_CODE_LENGTH).toUpperCase());

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

    // Hack to make the JSON more readable for preview, as _params is a String->String map
    public Map<String, Object> getJsonFriendlyParams()
    {
        Map<String, Object> params = new LinkedHashMap<>(getParams());
        Object jsonMetrics = params.get(MothershipReport.JSON_METRICS_KEY);
        if (jsonMetrics instanceof String jms)
        {
            JSONObject o = new JSONObject(jms);
            params.put(MothershipReport.JSON_METRICS_KEY, o);
        }
        return params;
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

    @Override
    public void run()
    {
        LOG.debug("Starting to submit report to " + _url);
        try
        {
            HttpURLConnection connection = openConnectionWithRedirects(_url, _forwardedFor);
            try
            {
                _responseCode = connection.getResponseCode();
                if (_responseCode == 200 && MOTHERSHIP_STATUS_SUCCESS.equals(connection.getHeaderField(MOTHERSHIP_STATUS_HEADER_NAME)))
                {
                    String encoding = StringUtilsLabKey.DEFAULT_CHARSET.name();
                    ContentType contentType = null;

                    if (connection.getContentType() != null)
                    {
                        contentType = new ContentType(connection.getContentType());
                        encoding = contentType.getParameter("charset");
                    }

                    try (InputStream in = connection.getInputStream())
                    {
                        if (contentType != null && contentType.getBaseType().equalsIgnoreCase("application/json"))
                        {
                            JSONObject json = new JSONObject(new JSONTokener(in));
                            if (json.has("data"))
                            {
                                JSONObject data = json.getJSONObject("data");
                                if (data.has("upgradeMessage"))
                                    _upgradeMessage = data.getString("upgradeMessage");

                                if (shouldReceiveMarketingUpdates())
                                {
                                    if (data.has("marketingUpdate"))
                                    {
                                        _marketingUpdate = data.getString("marketingUpdate");
                                    }
                                }
                            }
                        }
                        else
                        {
                            // legacy plain text response for mothership prior to 23.11
                            _upgradeMessage = IOUtils.toString(in, encoding);
                        }
                    }
                }
                LOG.debug("Successfully submitted report to " + _url);
            }
            finally
            {
                connection.disconnect();
            }
        }
        catch (Throwable t)
        {
            // Don't bother the client if this report fails
            LOG.debug("Failed to submit report to " + this._target + " at " + _url, t);
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
                    if (target.getProtocol().equals("http") || target.getProtocol().equals("https"))
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
        try (PrintWriter out = new PrintWriter(connection.getOutputStream(), true, StandardCharsets.UTF_8))
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
                    out.println(entry.getKey() + "=" + URLEncoder.encode(value, StringUtilsLabKey.DEFAULT_CHARSET));
                }
            }
        }

        connection.connect();
        return connection;
    }

    public void addServerSessionParams()
    {
        addParam("runtimeOS", System.getProperty("os.name"));
        addParam("javaVersion", System.getProperty("java.version"));

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
        if (_target == Target.test)
        {
            addParam("serverSessionGUID", GUID.makeGUID()); // Random session GUID for each test report
        }
        else
        {
            addParam("serverSessionGUID", AppProps.getInstance().getServerSessionGUID());
        }
        addParam("serverGUID", AppProps.getInstance().getServerGUID());

        ServletContext context = ModuleLoader.getServletContext();
        String servletContainer = context == null ? null : context.getServerInfo();
        addParam("servletContainer", servletContainer);
        addParam("distribution", AppProps.getInstance().getDistributionName());
        addParam("distributionFilename", AppProps.getInstance().getDistributionFilename());
        addParam("usageReportingLevel", AppProps.getInstance().getUsageReportingLevel().toString());
        addParam("exceptionReportingLevel", AppProps.getInstance().getExceptionReportingLevel().toString());
        addParam("apiVersion", "23.11");
    }

    public String getUpgradeMessage()
    {
        return _upgradeMessage;
    }

    public String getMarketingUpdate()
    {
        return _marketingUpdate;
    }

    public void setMetrics(Map<String, Object> metrics)
    {
        if (!metrics.isEmpty())
        {
            String serializedMetrics;
            try
            {
                serializedMetrics = JsonUtil.DEFAULT_MAPPER.writeValueAsString(metrics);
                addParam(JSON_METRICS_KEY, serializedMetrics);
            }
            catch (JsonProcessingException e)
            {
                LOG.error("Failed to serialize JSON metrics", e);
            }
        }
    }

    // The set of distributions that will receive the marketing message, just community for now
    private static final Set<String> MARKETING_RECEIVING_DISTRIBUTIONS = Set.of("community");

    // shouldReceiveMarketingUpdates() gets called on every single request, so check once and stash to optimize
    private static final boolean IS_MARKETING_RECEIVING_DISTRIBUTION = MARKETING_RECEIVING_DISTRIBUTIONS.contains(AppProps.getInstance().getDistributionName());

    public static boolean shouldReceiveMarketingUpdates()
    {
        return isSelfTestMarketingUpdates() || IS_MARKETING_RECEIVING_DISTRIBUTION;
    }

    public static void setSelfTestMarketingUpdates(boolean enabled)
    {
        _selfTestMarketingUpdates = enabled;
    }

    public static boolean isSelfTestMarketingUpdates()
    {
        return _selfTestMarketingUpdates;
    }
}
