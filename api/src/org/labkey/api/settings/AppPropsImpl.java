/*
 * Copyright (c) 2012-2019 LabKey Corporation
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

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerManager.RootContainerException;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.ExceptionReportingLevel;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MothershipReport;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.view.ActionURL;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * Mutable backing implementation for server-side application settings.
 * User: jeckels
 * Date: Jun 21, 2012
 */
class AppPropsImpl extends AbstractWriteableSettingsGroup implements AppProps
{
    private volatile String _contextPathStr;
    private volatile Path _contextPath = null;
    private volatile String _projectRoot = null;
    private volatile String _enlistmentId = null;

    private static Map<StashedStartupProperties, StartupPropertyEntry> _stashedProperties = Map.of();

    // Site settings constants are defined here in the same order as on the site settings page
    public enum SiteSettingsProperties implements StartupProperty
    {
        defaultDomain("Default email domain for authentication purposes. DO NOT USE... use Authentication.DefaultDomain instead.")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                AuthenticationManager.setDefaultDomain(UserManager.getGuestUser(), value);
                LOG.warn("Support for the \"SiteSettings.defaultDomain\" startup property will be removed shortly; use \"Authentication.DefaultDomain\" instead.");
            }
        },
        administratorContactEmail("Primary site administrator")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setAdministratorContactEmail(value);
            }
        },
        baseServerURL("Base server URL (used to create links in emails sent by the system)")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                try
                {
                    writeable.setBaseServerUrl(value);
                }
                catch (URISyntaxException e)
                {
                    throw new IllegalArgumentException("Invalid URI for property " + name() + ": " + value, e);
                }
            }
        },
        useContainerRelativeURL("Use \"path first\" urls (/home/project-begin.view)")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setUseContainerRelativeURL(Boolean.parseBoolean(value));
            }
        },
        usageReportingLevel("Check for updates and report usage statistics to the LabKey team. Valid values: [NONE, ON]")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setUsageReportingLevel(UsageReportingLevel.valueOf(value));
            }
        },
        exceptionReportingLevel("Report exceptions to the LabKey team. Valid values: [NONE, LOW, MEDIUM, HIGH]")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setExceptionReportingLevel(ExceptionReportingLevel.valueOf(value));
            }
        },
        selfReportExceptions("Report exceptions to the local server")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setSelfReportExceptions(Boolean.parseBoolean(value));
            }
        },
        memoryUsageDumpInterval("Log memory usage frequency in minutes, for debugging. Set to 0 to disable.")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {

            }
        },
        maxBLOBSize("Maximum file size, in bytes, to allow in database BLOBs")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {

            }
        },
        ext3Required("Require ExtJS v3.4.1 be loaded on each page (DEPRECATED)")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setExt3Required(Boolean.parseBoolean(value));
            }
        },
        ext3APIRequired("Require ExtJS v3.x based Client API be loaded on each page (DEPRECATED)")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setExt3APIRequired(Boolean.parseBoolean(value));
            }
        },
        sslRequired("Require SSL connections (users must connect via SSL)")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setSSLRequired(Boolean.parseBoolean(value));
            }
        },
        sslPort("SSL port number (specified in server config file)")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setSSLPort(Integer.parseInt(value));
            }
        },
        allowApiKeys("Let users create API keys")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setAllowApiKeys(Boolean.parseBoolean(value));
            }
        },
        apiKeyExpirationSeconds("API key expiration in seconds. -1 represents no expiration.")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setApiKeyExpirationSeconds(Integer.parseInt(value));
            }
        },
        allowSessionKeys("Let users create session keys, which are associated with the user's currents server session")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setAllowSessionKeys(Boolean.parseBoolean(value));
            }
        },
        pipelineToolsDirectory("A ';' separated list of directories on the web server containing executables that are run for pipeline jobs (e.g. TPP or XTandem)")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setPipelineToolsDir(value);
            }
        },
        showRibbonMessage("Display ribbon bar message")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setShowRibbonMessage(Boolean.parseBoolean(value));
            }
        },
        ribbonMessage("Ribbon bar message HTML")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setRibbonMessageHtml(value);
            }
        },
        adminOnlyMode("Admin only mode (only site admins may log in)")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setUserRequestedAdminOnlyMode(Boolean.parseBoolean(value));
            }
        },
        adminOnlyMessage("Message to users when site is in admin-only mode (Wiki formatting allowed)")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setAdminOnlyMessage(value);
            }
        },
        XFrameOption("Controls whether or not a browser may render a server page in a <frame> , <iframe> or <object>. Valid values: [SAMEORIGIN, ALLOW]")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setXFrameOptions(value);
            }
        },
        navAccessOpen("Always include inaccessible parent folders in project menu when child folder is accessible")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setNavAccessOpen(Boolean.parseBoolean(value));
            }
        };

        private final String _description;

        SiteSettingsProperties(String description)
        {
            _description = description;
        }

        @Override
        public String getDescription()
        {
            return _description;
        }

        public abstract void setValue(WriteableAppProps writeable, String value);
    }

    // Additional properties that are stored in the "SiteSettings" scope but not exposed on the site settings page
    public enum RandomStartupProperties implements StartupProperty
    {
        BLASTBaseURL("BLAST server URL")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.storeStringValue(BLAST_SERVER_BASE_URL_PROP, value);
            }
        },
        externalRedirectHostURLs("Allowed external redirect hosts")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setExternalRedirectHosts(Arrays.asList(StringUtils.split(value, EXTERNAL_REDIRECT_HOST_DELIMITER)));
            }
        },
        fileUploadDisabled("Disable file upload")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setFileUploadDisabled(Boolean.parseBoolean(value));
            }
        },
        mailRecorderEnabled("Record email messages sent")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setMailRecorderEnabled(Boolean.parseBoolean(value));
            }
        },
        userFileRoot("Enable personal folders for users")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setUserFilesRoot(value);
            }
        },
        webfilesEnabled("Alternative webfiles root")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setWebfilesEnabled(Boolean.parseBoolean(value));
            }
        },
        webRoot("Site-level file root")
        {
            @Override
            public void setValue(WriteableAppProps writeable, String value)
            {
                writeable.setFileSystemRoot(value);
            }
        };

        private final String _description;

        RandomStartupProperties(String description)
        {
            _description = description;
        }

        @Override
        public String getDescription()
        {
            return _description;
        }

        public abstract void setValue(WriteableAppProps writeable, String value);
    }

    static final String LOOK_AND_FEEL_REVISION = "logoRevision";
    static final String BASE_SERVER_URL_PROP = "baseServerURL";
    static final String DEFAULT_LSID_AUTHORITY_PROP = "defaultLsidAuthority";
    static final String PIPELINE_TOOLS_DIR_PROP = "pipelineToolsDirectory";
    static final String SSL_REQUIRED = "sslRequired";
    static final String SSL_PORT = "sslPort";
    static final String USER_REQUESTED_ADMIN_ONLY_MODE = "adminOnlyMode";
    static final String ADMIN_ONLY_MESSAGE = "adminOnlyMessage";
    static final String SHOW_RIBBON_MESSAGE = "showRibbonMessage";
    static final String RIBBON_MESSAGE = "ribbonMessage";
    static final String EXCEPTION_REPORTING_LEVEL = "exceptionReportingLevel";
    static final String USAGE_REPORTING_LEVEL = "usageReportingLevel";
    static final String ADMINISTRATOR_CONTACT_EMAIL = "administratorContactEmail";
    static final String BLAST_SERVER_BASE_URL_PROP = "BLASTBaseURL";
    static final String MEMORY_USAGE_DUMP_INTERVAL = "memoryUsageDumpInterval";
    static final String MAIL_RECORDER_ENABLED = "mailRecorderEnabled";
    static final String EXPERIMENTAL_FEATURE_PREFIX = EXPERIMENTAL_FEATURE + ".";
    static final String WEB_ROOT = "webRoot";
    static final String USER_FILE_ROOT = "userFileRoot";
    static final String WEBFILES_ROOT_ENABLED = "webfilesEnabled";
    static final String FILE_UPLOAD_DISABLED = "fileUploadDisabled";
    static final String MAX_BLOB_SIZE = "maxBLOBSize";
    static final String EXT3_REQUIRED = "ext3Required";
    static final String EXT3API_REQUIRED = "ext3APIRequired";
    static final String NAV_ACCESS_OPEN = "navAccessOpen";
    static final String SELF_REPORT_EXCEPTIONS = "selfReportExceptions";
    static final String USE_CONTAINER_RELATIVE_URL = "useContainerRelativeURL";
    static final String ALLOW_API_KEYS = "allowApiKeys";
    static final String API_KEY_EXPIRATION_SECONDS = "apiKeyExpirationSeconds";
    static final String ALLOW_SESSION_KEYS = "allowSessionKeys";
    static final String X_FRAME_OPTIONS = "XFrameOption";
    static final String EXTERNAL_REDIRECT_HOSTS = "externalRedirectHostURLs"; //configured redirect host urls (delimited by newline) will be saved under this property.
    static final String EXTERNAL_REDIRECT_HOST_DELIMITER = "\n";

    private static final String SERVER_GUID = "serverGUID";
    private static final String SERVER_GUID_XML_PARAMETER_NAME = "org.labkey.mothership." + SERVER_GUID;
    private static final String SITE_CONFIG_NAME = "SiteConfig";
    private static final String SERVER_SESSION_GUID = GUID.makeGUID();

    private static final Logger LOG = LogManager.getLogger(AppPropsImpl.class);

    @Override
    protected String getType()
    {
        return "site settings";
    }

    @Override
    protected String getGroupName()
    {
        return SITE_CONFIG_NAME;
    }

    @Override
    public String getContextPath()
    {
        if (_contextPathStr == null)
        {
            throw new IllegalStateException("Context path should have been set in ModuleLoader.doInit()");
        }
        return _contextPathStr;
    }

    @Override
    public Path getParsedContextPath()
    {
        return _contextPath;
    }

    @Override
    public void setContextPath(String contextPathStr)
    {
        _contextPathStr = contextPathStr;
        assert _contextPathStr.isEmpty() || _contextPathStr.startsWith("/");

        if (StringUtils.isEmpty(_contextPathStr))
            _contextPath = Path.rootPath;
        else
            _contextPath = Path.parse(getContextPath() + "/");
        assert _contextPath.isDirectory();
    }

    @Override
    public int getServerPort()
    {
        return getBaseServerProperties().getServerPort();
    }

    @Override
    public String getScheme()
    {
        return getBaseServerProperties().getScheme();
    }

    @Override
    public String getServerName()
    {
        return getBaseServerProperties().getServerName();
    }

    @Override
    public void ensureBaseServerUrl(HttpServletRequest request)
    {
        String baseServerUrl = getBaseServerUrl();

        if (null == baseServerUrl)
        {
            try
            {
                String initialRequestBaseServerUrl = URLHelper.getBaseServer(request.getScheme(), request.getServerName(), request.getServerPort()).toString();
                // Strip trailing slashes to avoid double slashes in generated links
                if(initialRequestBaseServerUrl.endsWith("/"))
                    initialRequestBaseServerUrl = initialRequestBaseServerUrl.substring(0, initialRequestBaseServerUrl.length() - 1);
                BaseServerProperties.validate(initialRequestBaseServerUrl);

                WriteableAppProps writeable = AppProps.getWriteableInstance();
                writeable.storeStringValue(BASE_SERVER_URL_PROP, initialRequestBaseServerUrl);
                writeable.save(null);
            }
            catch (URISyntaxException e)
            {
                throw new RuntimeException("Invalid initial request URL", e);
            }
        }
    }


    void validateBaseServerUrl(String baseServerUrl) throws URISyntaxException
    {
        BaseServerProperties.validate(baseServerUrl);
    }

    private BaseServerProperties getBaseServerProperties()
    {
        String baseServerUrl = getBaseServerUrl();

        if (null == baseServerUrl)
            throw new IllegalStateException("Base server URL is not yet set");

        return BaseServerProperties.parse(getBaseServerUrl());
    }

    @Override
    public boolean isSetBaseServerUrl()
    {
        return null != getBaseServerUrl();
    }

    @Override
    public String getBaseServerUrl()
    {
        return lookupStringValue(BASE_SERVER_URL_PROP, null);
    }

    // CONSIDER: All the following should probably be migrated into look & feel settings, making them overrideable at the project level

    @Override
    public String getHomePageUrl()
    {
        return getHomePageActionURL().getLocalURIString();
    }

    @Override
    public ActionURL getHomePageActionURL()
    {
        //noinspection ConstantConditions
        return PageFlowUtil.urlProvider(ProjectUrls.class).getHomeURL();
    }

    @Override
    public String getSiteWelcomePageUrlString()
    {
        return StringUtils.trimToNull(LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getCustomWelcome());
    }

    @Override
    public int getLookAndFeelRevision()
    {
        return lookupIntValue(LOOK_AND_FEEL_REVISION, 0);
    }

    @Override
    public String getDefaultLsidAuthority()
    {
        // Bad things happen if you change this value on an existing server, so making read-only per issue 26335
        String result = lookupStringValue(DEFAULT_LSID_AUTHORITY_PROP, "labkey.com");

        if (result == null || "".equals(result) || "NOT_SET".equals(result))
        {
            // We now prevent empty values but in case there's an installation that has one, convert to "localhost" for
            // backwards compatibility
            return "localhost";
        }
        return result;
    }

    @Override
    public String getPipelineToolsDirectory()
    {
        @SuppressWarnings("ConstantConditions")
        File webappDir = new File(ModuleLoader.getServletContext().getRealPath(""));
        File binDir = new File(webappDir.getParentFile(), "bin");

        return lookupStringValue(PIPELINE_TOOLS_DIR_PROP, binDir.getAbsolutePath());
    }

    @Override
    public boolean isSSLRequired()
    {
        return lookupBooleanValue(SSL_REQUIRED, false);
    }

    @Override
    public boolean isUserRequestedAdminOnlyMode()
    {
        return lookupBooleanValue(USER_REQUESTED_ADMIN_ONLY_MODE, false);
    }

    @Override
    public String getAdminOnlyMessage()
    {
        return lookupStringValue(ADMIN_ONLY_MESSAGE, "This site is currently undergoing maintenance, and only administrators can log in.");
    }

    @Override
    public boolean isShowRibbonMessage()
    {
        return lookupBooleanValue(SHOW_RIBBON_MESSAGE, false);
    }

    @Override
    public @Nullable String getRibbonMessageHtml()
    {
        return lookupStringValue(RIBBON_MESSAGE, null);
    }


    @Override
    public int getSSLPort()
    {
        return lookupIntValue(SSL_PORT, 443);
    }

    @Override
    public int getMemoryUsageDumpInterval()
    {
        return lookupIntValue(MEMORY_USAGE_DUMP_INTERVAL, 0);
    }

    @Override
    public int getMaxBLOBSize()
    {
        return lookupIntValue(MAX_BLOB_SIZE, 50_000_000);
    }

    @Override
    public boolean isExt3Required()
    {
        return lookupBooleanValue(EXT3_REQUIRED, false);
    }

    @Override
    public boolean isExt3APIRequired() { return lookupBooleanValue(EXT3API_REQUIRED, false); }

    @Override
    public boolean isSelfReportExceptions() { return MothershipReport.isShowSelfReportExceptions() && lookupBooleanValue(SELF_REPORT_EXCEPTIONS, true); }

    @Override
    public ExceptionReportingLevel getExceptionReportingLevel()
    {
        try
        {
            return ExceptionReportingLevel.valueOf(lookupStringValue(EXCEPTION_REPORTING_LEVEL, isDevMode() ? ExceptionReportingLevel.NONE.toString() : ExceptionReportingLevel.MEDIUM.toString()));
        }
        catch (IllegalArgumentException e)
        {
            return ExceptionReportingLevel.LOW;
        }
    }

    @Override
    public boolean isNavigationAccessOpen()
    {
        return lookupBooleanValue(NAV_ACCESS_OPEN, true);
    }

    @Override
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
            try (var ignore = SpringActionController.ignoreSqlUpdates())
            {
                WriteableAppProps writeable = AppProps.getWriteableInstance();
                writeable.storeStringValue(SERVER_GUID, serverGUID);
                writeable.save(null);
            }
            catch (RootContainerException e)
            {
                // Too early (during install) to save the GUID
            }
        }
        return serverGUID;
    }

    @Override
    public String getBLASTServerBaseURL()
    {
        return lookupStringValue(BLAST_SERVER_BASE_URL_PROP, "http://www.ncbi.nlm.nih.gov/blast/Blast.cgi?CMD=Web&amp;LAYOUT=TwoWindows&amp;AUTO_FORMAT=Semiauto&amp;ALIGNMENTS=50&amp;ALIGNMENT_VIEW=Pairwise&amp;CDD_SEARCH=on&amp;CLIENT=web&amp;COMPOSITION_BASED_STATISTICS=on&amp;DATABASE=nr&amp;DESCRIPTIONS=100&amp;ENTREZ_QUERY=(none)&amp;EXPECT=1000&amp;FILTER=L&amp;FORMAT_OBJECT=Alignment&amp;FORMAT_TYPE=HTML&amp;I_THRESH=0.005&amp;MATRIX_NAME=BLOSUM62&amp;NCBI_GI=on&amp;PAGE=Proteins&amp;PROGRAM=blastp&amp;SERVICE=plain&amp;SET_DEFAULTS.x=41&amp;SET_DEFAULTS.y=5&amp;SHOW_OVERVIEW=on&amp;END_OF_HTTPGET=Yes&amp;SHOW_LINKOUT=yes&amp;QUERY=");
    }

    @Override
    public String getServerSessionGUID()
    {
        return SERVER_SESSION_GUID;
    }

    @Override
    public boolean isMailRecorderEnabled()
    {
        return lookupBooleanValue(MAIL_RECORDER_ENABLED, false);
    }

    @Override
    public boolean isExperimentalFeatureEnabled(String feature)
    {
        feature = EXPERIMENTAL_FEATURE_PREFIX + feature;
        if (null != System.getProperty(feature))
            return Boolean.getBoolean(feature);
        return lookupBooleanValue(feature, false);
    }

    @Override
    public boolean isDevMode()
    {
        return Boolean.getBoolean("devmode");
    }

    @Override
    public @Nullable String getEnlistmentId()
    {
        return _enlistmentId;
    }

    @Override
    public boolean isCachingAllowed()
    {
        return Boolean.getBoolean("caching") || !isDevMode();
    }

    @Override
    public boolean isRecompileJspEnabled()
    {
        return isDevMode() && !Boolean.getBoolean("labkey.disableRecompileJsp");
    }

    /**
     * @inheritDoc
     *
     * Default Implementation.
     */
    @Override
    public boolean isIgnoreModuleSource()
    {
        return Boolean.getBoolean("labkey.ignoreModuleSource");
    }

    @Override
    public void setProjectRoot(String projectRoot)
    {
        _projectRoot = projectRoot;

        if (null != _projectRoot)
            _enlistmentId = ModuleLoader.getInstance().loadEnlistmentId(new File(_projectRoot));
    }

    @Override
    @Nullable
    public String getProjectRoot()
    {
        return _projectRoot;
    }

    @Override
    @Nullable
    public File getFileSystemRoot()
    {
        String webRoot = lookupStringValue(WEB_ROOT, "");
        if (!StringUtils.isEmpty(webRoot))
        {
            return new File(webRoot);
        }
        return null;
    }

    @Override
    @Nullable
    public File getUserFilesRoot()
    {
        String userRoot = lookupStringValue(USER_FILE_ROOT, "");
        if (!StringUtils.isEmpty(userRoot))
        {
            return new File(userRoot);
        }
        return null;
    }

    @Override
    @NotNull
    public UsageReportingLevel getUsageReportingLevel()
    {
        try
        {
            return UsageReportingLevel.valueOf(lookupStringValue(USAGE_REPORTING_LEVEL, isDevMode() ? UsageReportingLevel.NONE.toString() : UsageReportingLevel.ON.toString()));
        }
        catch (IllegalArgumentException e)
        {
            return UsageReportingLevel.ON;
        }
    }

    // Get the name of the webapp configuration file, e.g., labkey.xml, cpas.xml, or ROOT.xml.  Used in some error messages
    //  to provide suggestions to the admin.
    @Override
    public String getWebappConfigurationFilename()
    {
        String path = getContextPath();

        return "".equals(path) ? "ROOT.xml" : path.substring(1) + ".xml";
    }

    @Override
    public String getAdministratorContactEmail(boolean includeAppAdmins)
    {
        String defaultValue = null;
        List<Pair<Integer, String>> members = SecurityManager.getGroupMemberNamesAndIds(Group.groupAdministrators, false);

        // Issue 33403: add option to include application admins
        if (includeAppAdmins)
        {
            for (User appAdmin : UserManager.getAppAdmins())
                members.add(new Pair<>(appAdmin.getUserId(), appAdmin.getEmail()));
        }

        // Sort to find the minimum user id (i.e. oldest administrator's email address)
        members.sort(Entry.comparingByKey());
        
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

        // If that user is no longer a site admin, go back to the default value
        if (!validOptions.contains(result))
        {
            return defaultValue;
        }
        return result;
    }

    @NotNull
    @Override
    public String getReleaseVersion()
    {
        return ObjectUtils.defaultIfNull(ModuleLoader.getInstance().getCoreModule().getReleaseVersion(), UNKNOWN_VERSION);
    }

    @Override
    public double getSchemaVersion()
    {
        Double version = ModuleLoader.getInstance().getCoreModule().getSchemaVersion();
        if (null == version)
            throw new IllegalStateException("Core module schema version shouldn't be null");
        return version;
    }

    @Override
    public boolean getUseContainerRelativeURL()
    {
        return lookupBooleanValue(USE_CONTAINER_RELATIVE_URL, true);
    }

    @Override
    public boolean isAllowApiKeys()
    {
        return lookupBooleanValue(ALLOW_API_KEYS, false);
    }

    @Override
    public int getApiKeyExpirationSeconds()
    {
        return lookupIntValue(API_KEY_EXPIRATION_SECONDS, -1);
    }

    @Override
    public boolean isAllowSessionKeys()
    {
        return lookupBooleanValue(ALLOW_SESSION_KEYS, false);
    }

    @Override
    public String getXFrameOptions()
    {
        return lookupStringValue(X_FRAME_OPTIONS, "SAMEORIGIN");
    }

    @Override
    public String getStaticFilesPrefix()
    {
        // CURRENTLY SET using -Dstatic.files.prefix=//static.web.site.com
        // NOT IN UI, because one mistake will probably render the site unusable
        String s = System.getProperty("static.files.prefix");
        return trimToNull(s);
    }

    public static void populateSiteSettingsWithStartupProps()
    {
        // populate site settings with values from startup configuration as appropriate for prop modifier and isBootstrap flag
        // expects startup properties formatted like: SiteSettings.sslRequired;bootstrap=True
        // for a list of recognized site setting properties see the Available Site Settings action
        WriteableAppProps writeable = AppProps.getWriteableInstance();

        ModuleLoader.getInstance().handleStartupProperties(new StandardStartupPropertyHandler<>(SCOPE_SITE_SETTINGS, SiteSettingsProperties.class)
        {
            @Override
            public void handle(Map<SiteSettingsProperties, StartupPropertyEntry> properties)
            {
                properties.forEach((ssp, cp) -> {
                    LOG.info("Setting site settings startup property '" + ssp.name() + "' to '" + cp.getValue() + "'");
                    ssp.setValue(writeable, cp.getValue());
                });
            }
        });

        ModuleLoader.getInstance().handleStartupProperties(new StandardStartupPropertyHandler<>(SCOPE_SITE_SETTINGS, RandomStartupProperties.class)
        {
            @Override
            public void handle(Map<RandomStartupProperties, StartupPropertyEntry> properties)
            {
                properties.forEach((rsp, cp) -> {
                    LOG.info("Setting additional site-level startup property '" + rsp.name() + "' to '" + cp.getValue() + "'");
                    rsp.setValue(writeable, cp.getValue());
                });
            }
        });

        writeable.save(null);

        ModuleLoader.getInstance().handleStartupProperties(new StandardStartupPropertyHandler<>(SCOPE_SITE_SETTINGS, StashedStartupProperties.class)
        {
            @Override
            public void handle(Map<StashedStartupProperties, StartupPropertyEntry> properties)
            {
                _stashedProperties = properties;
            }
        });
    }

    @Override
    public boolean isWebfilesRootEnabled()
    {
        return lookupBooleanValue(WEBFILES_ROOT_ENABLED, false);
    }

    @Override
    public boolean isFileUploadDisabled()
    {
        return lookupBooleanValue(FILE_UPLOAD_DISABLED, false);
    }

    @Override
    @NotNull
    public List<String> getExternalRedirectHosts()
    {
        String urls =  lookupStringValue(EXTERNAL_REDIRECT_HOSTS, "");
        if (StringUtils.isNotBlank(urls))
        {
            return new ArrayList<>(Arrays.asList(urls.split(EXTERNAL_REDIRECT_HOST_DELIMITER)));
        }
        return new ArrayList<>();
    }

    @Override
    public Map<StashedStartupProperties, StartupPropertyEntry> getStashedProperties()
    {
        return _stashedProperties;
    }
}
