/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.api.settings;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.util.ExceptionReportingLevel;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MothershipReport;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UniqueID;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewServlet;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * User: jeckels
 * Date: Jun 21, 2012
 */
public class AppPropsImpl extends AbstractWriteableSettingsGroup implements AppProps.Interface
{
    private String _contextPathStr;
    private Path _contextPath = null;
    private int _serverPort = -1;
    private String _scheme;
    private String _serverName;
    private String _projectRoot = null;
    private String _enlistmentId = null;

    protected static final String LOOK_AND_FEEL_REVISION = "logoRevision";
    protected static final String DEFAULT_DOMAIN_PROP = "defaultDomain";
    protected static final String BASE_SERVER_URL_PROP = "baseServerURL";
    protected static final String DEFAULT_LSID_AUTHORITY_PROP = "defaultLsidAuthority";
    protected static final String PIPELINE_TOOLS_DIR_PROP = "pipelineToolsDirectory";
    protected static final String SSL_REQUIRED = "sslRequired";
    protected static final String SSL_PORT = "sslPort";
    protected static final String USER_REQUESTED_ADMIN_ONLY_MODE = "adminOnlyMode";
    protected static final String ADMIN_ONLY_MESSAGE = "adminOnlyMessage";
    protected static final String SHOW_RIBBON_MESSAGE = "showRibbonMessage";
    protected static final String RIBBON_MESSAGE = "ribbonMessage";
    protected static final String EXCEPTION_REPORTING_LEVEL = "exceptionReportingLevel";
    protected static final String USAGE_REPORTING_LEVEL = "usageReportingLevel";
    protected static final String ADMINISTRATOR_CONTACT_EMAIL = "administratorContactEmail";
    protected static final String SERVER_GUID = "serverGUID";
    protected static final String SERVER_GUID_XML_PARAMETER_NAME = "org.labkey.mothership." + SERVER_GUID;
    protected static final String BLAST_SERVER_BASE_URL_PROP = "BLASTBaseURL";
    protected static final String MASCOT_SERVER_PROP = "MascotServer";
    protected static final String MASCOT_USERACCOUNT_PROP = "MascotUserAccount";
    protected static final String MASCOT_USERPASSWORD_PROP = "MascotUserPassword";
    protected static final String MASCOT_HTTPPROXY_PROP = "MascotHTTPProxy";
    protected static final String MEMORY_USAGE_DUMP_INTERVAL = "memoryUsageDumpInterval";
    protected static final String NETWORK_DRIVE_LETTER = "networkDriveLetter";
    protected static final String NETWORK_DRIVE_PATH = "networkDrivePath";
    protected static final String NETWORK_DRIVE_USER = "networkDriveUser";
    protected static final String NETWORK_DRIVE_PASSWORD = "networkDrivePassword";
    protected static final String MAIL_RECORDER_ENABLED = "mailRecorderEnabled";
    protected static final String EXPERIMENTAL_FEATURE_PREFIX = "experimentalFeature.";
    protected static final String WEB_ROOT = "webRoot";
    protected static final String MAX_BLOB_SIZE = "maxBLOBSize";
    protected static final String EXT3_REQUIRED = "ext3Required";
    protected static final String EXT3API_REQUIRED = "ext3APIRequired";
    protected static final String SELF_REPORT_EXCEPTIONS = "selfReportExceptions";

    protected static final String SITE_CONFIG_NAME = "SiteConfig";

    private static final String SERVER_SESSION_GUID = GUID.makeGUID();

    protected String getType()
    {
        return "site settings";
    }

    protected String getGroupName()
    {
        return SITE_CONFIG_NAME;
    }

    public String getContextPath()
    {
        if (_contextPathStr == null)
        {
            throw new IllegalStateException("Unable to determine the context path before a request has come in");
        }
        return _contextPathStr;
    }

    public Path getParsedContextPath()
    {
        return _contextPath;
    }

    public int getServerPort()
    {
        if (_serverPort == -1)
        {
            throw new IllegalStateException("Unable to determine the server port before a request has come in");
        }
        return _serverPort;
    }

    public String getScheme()
    {
        if (_scheme == null)
        {
            throw new IllegalStateException("Unable to determine the scheme before a request has come in");
        }
        return _scheme;
    }

    public String getServerName()
    {
        if (_serverName == null)
        {
            throw new IllegalStateException("Unable to determine the server name before a request has come in");
        }
        return _serverName;
    }

    @Override
    public boolean isBaseServerUrlInitialized()
    {
        return _serverName != null;
    }

    public void initializeFromRequest(HttpServletRequest request)
    {
        // Should be called once at first request
        assert null == _contextPathStr;
        assert null == _serverName && null == _scheme && -1 == _serverPort;

        _contextPathStr = request.getContextPath();
        assert _contextPathStr.isEmpty() || _contextPathStr.startsWith("/");

        if (StringUtils.isEmpty(_contextPathStr))
            _contextPath = Path.rootPath;
        else
            _contextPath = Path.parse(getContextPath() + "/");
        assert _contextPath.isDirectory();

        String baseServerUrl = lookupStringValue(BASE_SERVER_URL_PROP, null);

        if (baseServerUrl != null)
        {
            try
            {
                setBaseServerUrlAttributes(baseServerUrl);
                return;
            }
            catch (URISyntaxException e)
            {
                // Ignore -- just use request
            }
        }

        _serverPort = request.getServerPort();
        _scheme = request.getScheme();
        _serverName = request.getServerName();


    }


    // Update the cached base server url attributes. Very important to validate this URL, #17625
    public void setBaseServerUrlAttributes(String baseServerUrl) throws URISyntaxException
    {
        // First, validate URL using Commons Validator
        if (!new UrlValidator(new String[] {"http", "https"}, UrlValidator.ALLOW_LOCAL_URLS).isValid(baseServerUrl))
            throw new URISyntaxException(baseServerUrl, "Invalid URL");

        // Divide up the parts and validate some more
        URLHelper url = new URLHelper(baseServerUrl);

        if (url.getParsedPath().size() > 0)
            throw new URISyntaxException(baseServerUrl, "Too many path parts");

        String scheme = url.getScheme();
        String serverName = url.getHost();

        if (null == serverName)
            throw new URISyntaxException(baseServerUrl, "Invalid server name");

        int serverPort;

        if (url.getPort() != -1)
        {
            serverPort = url.getPort();
        }
        else
        {
            switch (scheme)
            {
                case "http":
                    serverPort = 80;
                    break;
                case "https":
                    serverPort = 443;
                    break;
                default:
                    throw new URISyntaxException(baseServerUrl, "Invalid scheme");
            }
        }

        // New values have been validated -- now update global settings
        _scheme = scheme;
        _serverName = serverName;
        _serverPort = serverPort;

        // One last check... are we able to use ActionURL now?
        try
        {
            ActionURL actionUrl = new ActionURL();
            actionUrl.getURIString();
        }
        catch (Throwable t)
        {
            throw new URISyntaxException(baseServerUrl, "Invalid URL");
        }
    }


    // Mock up a request using cached port, server name, etc.  Use when sending email from background threads (when
    // a request is not available).
   // TODO: Reconcile this and ViewServlet.mockRequest()... shouldn't these be the same?
    public HttpServletRequest createMockRequest()
    {
        MockHttpServletRequest request = new MockHttpServletRequest(ViewServlet.getViewServletContext());

        request.setContextPath(getContextPath());
        request.setServerPort(getServerPort());
        request.setServerName(getServerName());
        request.setScheme(getScheme());
        UniqueID.initializeRequestScopedUID(request);

        return request;
    }

    public String getDefaultDomain()
    {
        return lookupStringValue(DEFAULT_DOMAIN_PROP, "");
    }

    public String getBaseServerUrl()
    {
        return URLHelper.getBaseServer(_scheme, _serverName, _serverPort).toString();
    }

    // CONSIDER: All the following should probably be migrated into look & feel settings, making them overrideable at the project level

    public String getHomePageUrl()
    {
        return getHomePageActionURL().getLocalURIString();
    }

    public ActionURL getHomePageActionURL()
    {
        return PageFlowUtil.urlProvider(ProjectUrls.class).getHomeURL();
    }

    public int getLookAndFeelRevision()
    {
        return lookupIntValue(LOOK_AND_FEEL_REVISION, 0);
    }

    public String getDefaultLsidAuthority()
    {
        String result = lookupStringValue(DEFAULT_LSID_AUTHORITY_PROP, "localhost");
        if (result == null || "".equals(result))
        {
            // We now prevent empty values but in case there's an installation that has one, convert to "localhost"
            return "localhost";
        }
        return result;
    }

    public String getPipelineToolsDirectory()
    {
        File webappDir = new File(ModuleLoader.getServletContext().getRealPath(""));
        File binDir = new File(webappDir.getParentFile(), "bin");

        return lookupStringValue(PIPELINE_TOOLS_DIR_PROP, binDir.getAbsolutePath());
    }

    public boolean isSSLRequired()
    {
        return lookupBooleanValue(SSL_REQUIRED, false);
    }

    public boolean isUserRequestedAdminOnlyMode()
    {
        return lookupBooleanValue(USER_REQUESTED_ADMIN_ONLY_MODE, false);
    }

    public String getAdminOnlyMessage()
    {
        return lookupStringValue(ADMIN_ONLY_MESSAGE, "This site is currently undergoing maintenance, and only administrators can log in.");
    }

    public boolean isShowRibbonMessage()
    {
        return lookupBooleanValue(SHOW_RIBBON_MESSAGE, false);
    }

    public String getRibbonMessageHtml()
    {
        return lookupStringValue(RIBBON_MESSAGE, null);
    }


    public int getSSLPort()
    {
        return lookupIntValue(SSL_PORT, 443);
    }

    public int getMemoryUsageDumpInterval()
    {
        return lookupIntValue(MEMORY_USAGE_DUMP_INTERVAL, 0);
    }

    public int getMaxBLOBSize()
    {
        return lookupIntValue(MAX_BLOB_SIZE, 50000000);
    }

    public boolean isExt3Required()
    {
        return lookupBooleanValue(EXT3_REQUIRED, false);
    }

    public boolean isExt3APIRequired() { return lookupBooleanValue(EXT3API_REQUIRED, false); }

    public boolean isSelfReportExceptions() { return MothershipReport.isShowSelfReportExceptions() && lookupBooleanValue(SELF_REPORT_EXCEPTIONS, true); }

    public ExceptionReportingLevel getExceptionReportingLevel()
    {
        try
        {
            // Ensure that dev machines are setup with High reporting levels.  That way, if they test in production mode we'll be able to filter out those exception reports.
            return ExceptionReportingLevel.valueOf(lookupStringValue(EXCEPTION_REPORTING_LEVEL, isDevMode() ? ExceptionReportingLevel.HIGH.toString() : ExceptionReportingLevel.MEDIUM.toString()));
        }
        catch (IllegalArgumentException e)
        {
            return ExceptionReportingLevel.LOW;
        }
    }

    public String getServerGUID()
    {
        ServletContext context = ModuleLoader.getServletContext();
        if (context != null)
        {
            String serverGUID = context.getInitParameter(SERVER_GUID_XML_PARAMETER_NAME);
            if (serverGUID != null)
            {
                return serverGUID;
            }
        }
        String serverGUID = lookupStringValue(SERVER_GUID, SERVER_SESSION_GUID);
        if (serverGUID.equals(SERVER_SESSION_GUID))
        {
            WriteableAppProps writeable = AppProps.getWriteableInstance();
            writeable.storeStringValue(SERVER_GUID, serverGUID);
            writeable.save();
        }
        return serverGUID;
    }

    public String getBLASTServerBaseURL()
    {
        return lookupStringValue(BLAST_SERVER_BASE_URL_PROP, "http://www.ncbi.nlm.nih.gov/blast/Blast.cgi?CMD=Web&amp;LAYOUT=TwoWindows&amp;AUTO_FORMAT=Semiauto&amp;ALIGNMENTS=50&amp;ALIGNMENT_VIEW=Pairwise&amp;CDD_SEARCH=on&amp;CLIENT=web&amp;COMPOSITION_BASED_STATISTICS=on&amp;DATABASE=nr&amp;DESCRIPTIONS=100&amp;ENTREZ_QUERY=(none)&amp;EXPECT=1000&amp;FILTER=L&amp;FORMAT_OBJECT=Alignment&amp;FORMAT_TYPE=HTML&amp;I_THRESH=0.005&amp;MATRIX_NAME=BLOSUM62&amp;NCBI_GI=on&amp;PAGE=Proteins&amp;PROGRAM=blastp&amp;SERVICE=plain&amp;SET_DEFAULTS.x=41&amp;SET_DEFAULTS.y=5&amp;SHOW_OVERVIEW=on&amp;END_OF_HTTPGET=Yes&amp;SHOW_LINKOUT=yes&amp;QUERY=");
    }

    public boolean hasMascotServer()
    {
        return !"".equals(getMascotServer());
    }

    public String getMascotServer()
    {
        return lookupStringValue(MASCOT_SERVER_PROP, "");
    }

    public String getMascotUserAccount()
    {
        return lookupStringValue(MASCOT_USERACCOUNT_PROP, "");
    }

    public String getMascotUserPassword()
    {
        return lookupStringValue(MASCOT_USERPASSWORD_PROP, "");
    }

    public String getMascotHTTPProxy()
    {
        return lookupStringValue(MASCOT_HTTPPROXY_PROP, "");
    }

    public String getNetworkDriveLetter()
    {
        return lookupStringValue(NETWORK_DRIVE_LETTER, "");
    }

    public String getNetworkDrivePath()
    {
        return lookupStringValue(NETWORK_DRIVE_PATH, "");
    }

    public String getNetworkDriveUser()
    {
        return lookupStringValue(NETWORK_DRIVE_USER, "");
    }

    public String getNetworkDrivePassword()
    {
        return lookupStringValue(NETWORK_DRIVE_PASSWORD, "");
    }

    public String getServerSessionGUID()
    {
        return SERVER_SESSION_GUID;
    }

    public boolean isMailRecorderEnabled()
    {
        return lookupBooleanValue(MAIL_RECORDER_ENABLED, false);
    }

    public boolean isExperimentalFeatureEnabled(String feature)
    {
        feature = EXPERIMENTAL_FEATURE_PREFIX + feature;
        if (null != System.getProperty(feature))
            return Boolean.getBoolean(feature);
        return lookupBooleanValue(feature, false);
    }

    public boolean isDevMode()
    {
        return Boolean.getBoolean("devmode");
    }

    @Override
    public @Nullable String getEnlistmentId()
    {
        return _enlistmentId;
    }

    public boolean isCachingAllowed()
    {
        return Boolean.getBoolean("caching") || !isDevMode();
    }

    public boolean isRecompileJspEnabled()
    {
        return isDevMode() && !Boolean.getBoolean("labkey.disableRecompileJsp");
    }

    public void setProjectRoot(String projectRoot)
    {
        _projectRoot = projectRoot;

        if (null != _projectRoot)
        {
            File file = new File(_projectRoot, "enlistment.properties");

            if (file.exists())
            {
                Properties props = new Properties();

                try (InputStream is = new FileInputStream(file))
                {
                    props.load(is);
                    _enlistmentId = props.getProperty("enlistment.id");
                }
                catch (IOException e)
                {
                    ExceptionUtil.logExceptionToMothership(null, e);
                }
            }
        }
    }

    // Return the root of the main source tree
    public @Nullable String getProjectRoot()
    {
        return _projectRoot;
    }

    public File getFileSystemRoot()
    {
        String webRoot = lookupStringValue(WEB_ROOT, "");
        if (!StringUtils.isEmpty(webRoot))
        {
            return new File(webRoot);
        }
        return null;
    }

    public UsageReportingLevel getUsageReportingLevel()
    {
        try
        {
            return UsageReportingLevel.valueOf(lookupStringValue(USAGE_REPORTING_LEVEL, UsageReportingLevel.MEDIUM.toString()));
        }
        catch (IllegalArgumentException e)
        {
            return UsageReportingLevel.LOW;
        }
    }

    // Get the name of the webapp configuration file, e.g., labkey.xml, cpas.xml, or root.xml.  Used in some error messages
    //  to provide suggestions to the admin.
    public String getWebappConfigurationFilename()
    {
        // Would rather determine the context filename from ModuleLoader.getServletContext(), but there appears to be
        //  no way to do this.

        String path = getContextPath();

        return "".equals(path) ? "root.xml" : path.substring(1) + ".xml";
    }

    @Override
    public String getAdministratorContactEmail()
    {
        String defaultValue = null;
        // Default to the oldest site administrator's email address
        List<Pair<Integer, String>> members = SecurityManager.getGroupMemberNamesAndIds("Administrators");
        // Sort to find the minimum user id
        Collections.sort(members, new Comparator<Pair<Integer, String>>()
        {
            public int compare(Pair<Integer, String> o1, Pair<Integer, String> o2)
            {
                return o1.getKey().compareTo(o2.getKey());
            }
        });
        Set<String> validOptions = new HashSet<>();
        for (Pair<Integer, String> entry : members)
        {
            validOptions.add(entry.getValue());
            if (defaultValue == null && entry.getValue() != null)
            {
                defaultValue = entry.getValue();
            }
        }
        String result = lookupStringValue(ADMINISTRATOR_CONTACT_EMAIL, defaultValue);

        // If that user is no long a site admin, go back to the default value
        if (!validOptions.contains(result))
        {
            return defaultValue;
        }
        return result;
    }

    public String getLabKeyVersionString()
    {
        DecimalFormat format = new DecimalFormat("0.00");
        return format.format(ModuleLoader.getInstance().getCoreModule().getVersion());
    }

    final static boolean useContainerRelativeURLByDefault = false;

    @Override
    public boolean getUseContainerRelativeURL()
    {
        return useContainerRelativeURLByDefault || isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_CONTAINER_RELATIVE_URL);
    }

    @Override
    public boolean isTeamCityEnviornment()
    {
        String buildConfName = System.getProperty("teamcity.build.id");
        return StringUtils.isNotBlank(buildConfName);
    }
}
