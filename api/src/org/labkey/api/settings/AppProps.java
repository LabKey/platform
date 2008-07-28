/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.util.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ThemeFont;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.WebTheme;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.Date;
import java.util.Map;
import java.util.Random;

/**
 * User: arauch
 * Date: Apr 11, 2005
 * Time: 1:10:18 PM
 */
public class AppProps
{
    private static AppProps ourInstance = null;

    private static Logger _log = Logger.getLogger(AppProps.class);

    private static String _contextPath;
    private static int _serverPort = -1;
    private static String _scheme;
    private static String _serverName;
    private static String _projectRoot = null;
    private static String _homePageURLString = null;
    private static ActionURL _homePageURL = null;

    protected static final String DEFAULT_DOMAIN_PROP = "defaultDomain";
    protected static final String COMPANY_NAME_PROP = "companyName";
    protected static final String LOGO_HREF_PROP = "logoHref";
    protected static final String LOOK_AND_FEEL_REVISION = "logoRevision";
    protected static final String REPORT_A_PROBLEM_PATH_PROP = "reportAProblemPath";
    protected static final String BASE_SERVER_URL_PROP = "baseServerURL";
    protected static final String SYSTEM_DESCRIPTION_PROP = "systemDescription";
    protected static final String SYSTEM_EMAIL_ADDRESS_PROP = "systemEmailAddress";
    protected static final String SYSTEM_SHORT_NAME_PROP = "systemShortName";
    protected static final String DEFAULT_LSID_AUTHORITY_PROP = "defaultLsidAuthority";
    protected static final String THEME_NAME_PROP = "themeName";
    protected static final String PIPELINE_PERL_CLUSTER_PROP = "hasPipelineCluster";
    protected static final String PIPELINE_TOOLS_DIR_PROP = "pipelineToolsDirectory";    
    protected static final String PIPELINE_FTPHOST_PROP = "pipelineFTPHost";
    protected static final String PIPELINE_FTPPORT_PROP = "pipelineFTPPort";
    protected static final String PIPELINE_FTPSECURE_PROP = "pipelineFTPSecure";
    protected static final String SEQUEST_SERVER_PROP = "SequestServer";
    protected static final String SSL_REQUIRED = "sslRequired";
    protected static final String SSL_PORT = "sslPort";
    protected static final String USER_REQUESTED_ADMIN_ONLY_MODE = "adminOnlyMode";
    protected static final String ADMIN_ONLY_MESSAGE = "adminOnlyMessage";
    protected static final String EXCEPTION_REPORTING_LEVEL = "exceptionReportingLevel";
    protected static final String USAGE_REPORTING_LEVEL = "usageReportingLevel";
    protected static final String SERVER_GUID = "serverGUID";
    protected static final String MICROARRAY_FEATURE_EXTRACTION_SERVER_PROP = "microarrayFeatureExtractionServer";
    protected static final String MASCOT_SERVER_PROP = "MascotServer";
    protected static final String MASCOT_USERACCOUNT_PROP = "MascotUserAccount";
    protected static final String MASCOT_USERPASSWORD_PROP = "MascotUserPassword";
    protected static final String MASCOT_HTTPPROXY_PROP = "MascotHTTPProxy";
    protected static final String THEME_FONT_PROP = "themeFont";
    protected static final String SYSTEM_MAINTENANCE_INTERVAL = "systemMaintenanceInterval";  // NEVER, DAILY
    protected static final String SYSTEM_MAINTENANCE_TIME = "systemMaintenanceTime"; // 02:00
    protected static final String MEMORY_USAGE_DUMP_INTERVAL = "memoryUsageDumpInterval";
    protected static final String NAVIGATION_BAR_WIDTH = "navigationBarWidth";
    protected static final String NETWORK_DRIVE_LETTER = "networkDriveLetter";
    protected static final String NETWORK_DRIVE_PATH = "networkDrivePath";
    protected static final String NETWORK_DRIVE_USER = "networkDriveUser";
    protected static final String NETWORK_DRIVE_PASSWORD = "networkDrivePassword";
    protected static final String FOLDER_DISPLAY_MODE = "folderDisplayMode";
    protected static final String CABIG_ENABLED = "caBIGEnabled";
    protected static final String CALLBACK_PASSWORD_PROP = "callbackPassword";

    private static final String SERVER_SESSION_GUID = GUID.makeGUID();

    public static synchronized AppProps getInstance()
    {
        if (null == ourInstance)
            ourInstance = new AppProps();

        return ourInstance;
    }

    private String lookupJNDIStringValue(String name)
    {
        try
        {
            InitialContext ctx = new InitialContext();
            Context envCtx = (Context) ctx.lookup("java:comp/env");
            return (String) envCtx.lookup(name);
        }
        catch (NamingException e)
        {
            // That's OK, this is an old place to store the values anyway
            return null;
        }
    }

    private boolean lookupBooleanValue(String name, boolean defaultValue)
    {
        return "TRUE".equalsIgnoreCase(lookupStringValue(name, defaultValue ? "TRUE" : "FALSE" ) );
    }

    private int lookupIntValue(String name, int defaultValue)
    {
        try
        {
            return Integer.parseInt(lookupStringValue(name, Integer.toString(defaultValue)));
        }
        catch(NumberFormatException e)
        {
            return defaultValue;
        }
    }

    protected Map<String, String> getProperties() throws SQLException
    {
        return PropertyManager.getSiteConfigProperties();
    }

    private String lookupStringValue(String name, String defaultValue)
    {
        try
        {
            Map props = getProperties();
            String value = (String) props.get(name);
            if (value != null)
            {
                return value;
            }

            // Upgrade from CPAS 1.0 case... values aren't in the database yet, get them from cpas.xml
            value = lookupJNDIStringValue(name);

            if (value == null)
            {
                value = defaultValue;
            }

            if (value != null)
            {
                PropertyManager.PropertyMap p = PropertyManager.getWritableSiteConfigProperties();
                p.put(name, value);
                PropertyManager.saveProperties(p);
            }

            return value;
        }
        catch (SQLException e)
        {
            // Real database problem... log it and return default
            _log.error("Problem getting property value", e);
        }

        return defaultValue;
    }

    public void setContextPath(HttpServletRequest request)
    {
        // Should be called once at first request
        assert null == _contextPath;

        _contextPath = request.getContextPath();
    }

    public String getContextPath()
    {
        if (_contextPath == null)
        {
            throw new IllegalStateException("Unable to determine the context path before a request has come in");
        }
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

    public void initializeBaseServerUrl(HttpServletRequest request)
    {
        assert null == _serverName && null == _scheme && -1 == _serverPort;     // Should be called once at first request

        String baseServerUrl = lookupStringValue(BASE_SERVER_URL_PROP, null);

        if (baseServerUrl != null)
        {
            try
            {
                setBaseServerUrlAttributes(baseServerUrl);

/*              TODO: Turn into test case

                setBaseServerAttributes("https://localhost/");
                setBaseServerAttributes("http://localhost:8080");
                setBaseServerAttributes("http://localhost");
                setBaseServerAttributes("https://localhost/");
                setBaseServerAttributes("https://localhost/notallowed");  // Should error
*/
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


    // Update the cached base server url attributes.
    public void setBaseServerUrlAttributes(String baseServerUrl) throws URISyntaxException
    {
        URLHelper url = new URLHelper(baseServerUrl);

        if (url.getPathParts().length > 0)
            throw new URISyntaxException(baseServerUrl, "Too many path parts");

        String scheme = url.getScheme();
        String serverName = url.getHost();
        int serverPort;

        if (url.getPort() != -1)
        {
            serverPort = url.getPort();
        }
        else
        {
            if ("http".equals(scheme))
                serverPort = 80;
            else if ("https".equals(scheme))
                serverPort = 443;
            else
                throw new URISyntaxException(baseServerUrl, "Invalid scheme");
        }

        // New values have been validated -- now update global settings
        _scheme = scheme;
        _serverName = serverName;
        _serverPort = serverPort;
    }


    // Mock up a request using cached port, server name, etc.  Use when sending email from background threads (when
    // a request is not available).
    public HttpServletRequest createMockRequest()
    {
        MockHttpServletRequest request = new MockHttpServletRequest(ViewServlet.getViewServletContext());

        request.setContextPath(getContextPath());
        request.setServerPort(getServerPort());
        request.setServerName(getServerName());
        request.setScheme(getScheme());

        return request;
    }


    public String getDefaultDomain()
    {
        return lookupStringValue(DEFAULT_DOMAIN_PROP, "");
    }

    public String getHomePageName()
    {
        //return getSystemShortName() + " Home";
        // Per George's request, try dropping app name from homepage link title:
        return "Home";
    }

    public String getHomePageUrl()
    {
        if (null == _homePageURLString)
            _homePageURLString = getHomePageActionURL().getLocalURIString();

        return _homePageURLString;
    }

    public ActionURL getHomePageActionURL()
    {
        if (null == _homePageURL)
            _homePageURL = new ActionURL("project", "begin.view", "/home");

        return _homePageURL;
    }

    public String getCompanyName()
    {
        return lookupStringValue(COMPANY_NAME_PROP, "Demo Installation");
    }

    public String getLogoHref()
    {
        return lookupStringValue(LOGO_HREF_PROP, getHomePageUrl());
    }

    public String getUnsubstitutedReportAProblemPath()
    {
        return lookupStringValue(REPORT_A_PROBLEM_PATH_PROP, "${contextPath}/project" + Container.DEFAULT_SUPPORT_PROJECT_PATH + "/begin.view");
    }

    public String getReportAProblemPath()
    {
        String path = getUnsubstitutedReportAProblemPath();

        if ("/dev/issues".equals(path))
        {
            try
            {
                path = "${contextPath}/Issues/dev/issues/insert.view";
                WriteableAppProps writeable = getWriteableInstance();
                writeable.setReportAProblemPath(path);
                writeable.save();
            }
            catch (SQLException e)
            {
                _log.error("Unable to reset ReportAProblem", e);
            }
        }

        return path.replace("${contextPath}", getContextPath());
    }

    public String getBaseServerUrl()
    {
        return URLHelper.getBaseServer(_scheme, _serverName, _serverPort).toString();
    }

    public String getSystemDescription()
    {
        return lookupStringValue(SYSTEM_DESCRIPTION_PROP, "");
    }

    public String getSystemEmailAddress()
    {
        return lookupStringValue(SYSTEM_EMAIL_ADDRESS_PROP, "cpas@fhcrc.org");
    }

    public String getSystemShortName()
    {
        return lookupStringValue(SYSTEM_SHORT_NAME_PROP, "LabKey Server");
    }

    public String getDefaultLsidAuthority()
    {
        return lookupStringValue(DEFAULT_LSID_AUTHORITY_PROP, "localhost");
    }

    public String getThemeName()
    {
        return lookupStringValue(THEME_NAME_PROP, WebTheme.DEFAULT_THEME.toString());
    }

    public boolean isPerlPipelineEnabled()
    {
        return lookupBooleanValue(PIPELINE_PERL_CLUSTER_PROP, false);
    }

    public String getPipelineToolsDirectory()
    {
        return lookupStringValue(PIPELINE_TOOLS_DIR_PROP, "");
    }

    public boolean hasSequest()
    {
        return !lookupStringValue(SEQUEST_SERVER_PROP, "").equals("");
    }

    public String getSequestServer()
    {
        return lookupStringValue(SEQUEST_SERVER_PROP, "");
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

    public int getSSLPort()
    {
        return lookupIntValue(SSL_PORT, 443);
    }

    public String getSystemMaintenanceInterval()
    {
        return lookupStringValue(SYSTEM_MAINTENANCE_INTERVAL, "daily");
    }

    public Date getSystemMaintenanceTime()
    {
        String time = lookupStringValue(SYSTEM_MAINTENANCE_TIME, "2:00");
        return SystemMaintenance.parseSystemMaintenanceTime(time);
    }

    public int getMemoryUsageDumpInterval()
    {
        return lookupIntValue(MEMORY_USAGE_DUMP_INTERVAL, 0);
    }

    public int getLookAndFeelRevision()
    {
        return lookupIntValue(LOOK_AND_FEEL_REVISION, 0);
    }

    public ExceptionReportingLevel getExceptionReportingLevel()
    {
        try
        {
            // Ensure that dev machines are setup with High reporting levels.  That way, if they test in production mode we'll be able to filter out those exception reports.
            return ExceptionReportingLevel.valueOf(lookupStringValue(EXCEPTION_REPORTING_LEVEL, isDevMode() ? ExceptionReportingLevel.HIGH.toString() : ExceptionReportingLevel.LOW.toString()));
        }
        catch (IllegalArgumentException e)
        {
            return ExceptionReportingLevel.LOW;
        }
    }

    public String getServerGUID()
    {
        return lookupStringValue(SERVER_GUID, SERVER_SESSION_GUID);
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

    public String getPipelineFTPHost()
    {
        return lookupStringValue(PIPELINE_FTPHOST_PROP,"");
    }

    public String getPipelineFTPPort()
    {
        return lookupStringValue(PIPELINE_FTPPORT_PROP,"");
    }

    public boolean isPipelineFTPSecure()
    {
        return lookupBooleanValue(PIPELINE_FTPSECURE_PROP, false);
    }

    public String getThemeFont()
    {
        return lookupStringValue(THEME_FONT_PROP, ThemeFont.DEFAULT_THEME_FONT.getFriendlyName());
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

    public boolean isDevMode()
    {
        return Boolean.getBoolean("devmode");
    }

    public boolean isCachingAllowed()
    {
        return Boolean.getBoolean("caching") || !isDevMode();
    }

    public void setProjectRoot(String projectRoot)
    {
        _projectRoot = projectRoot;
    }

    // Return the root of the main source tree
    public String getProjectRoot()
    {
        return _projectRoot;
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

    public static WriteableAppProps getWriteableInstance() throws SQLException
    {
        return new WriteableAppProps();
    }

    public String getNavigationBarWidth()
    {
        return lookupStringValue(NAVIGATION_BAR_WIDTH, "146");
    }

    public boolean isCaBIGEnabled()
    {
        return lookupBooleanValue(CABIG_ENABLED, false);
    }

    public FolderDisplayMode getFolderDisplayMode()
    {
        try
        {
            FolderDisplayMode mode = FolderDisplayMode.fromString(lookupStringValue(FOLDER_DISPLAY_MODE, FolderDisplayMode.ALWAYS.toString()));
            return mode; 
        }
        catch (IllegalArgumentException e)
        {
            return FolderDisplayMode.ALWAYS;
        }
    }

    public String getMicroarrayFeatureExtractionServer()
    {
        return lookupStringValue(MICROARRAY_FEATURE_EXTRACTION_SERVER_PROP, "");
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

    public String getCallbackPassword()
    {
        StringBuilder defaultPassword = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 8; i++)
        {
            int x = random.nextInt(26 + 26 + 10);
            int letter;
            if (x < 26)
            {
                letter = 'a' + x;
            }
            else if (x < 52)
            {
                letter = 'A' + x - 26;
            }
            else
            {
                letter = '0' + x - 52;
            }
            defaultPassword.append((char)letter);
        }
        return lookupStringValue(CALLBACK_PASSWORD_PROP, defaultPassword.toString());
    }
}
