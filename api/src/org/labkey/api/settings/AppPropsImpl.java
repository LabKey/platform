/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerManager.RootContainerException;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.SecurityManager;
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
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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

    static final String LOOK_AND_FEEL_REVISION = "logoRevision";
    static final String DEFAULT_DOMAIN_PROP = "defaultDomain";
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
    static final String NETWORK_DRIVE_LETTER = "networkDriveLetter";
    static final String NETWORK_DRIVE_PATH = "networkDrivePath";
    static final String NETWORK_DRIVE_USER = "networkDriveUser";
    static final String NETWORK_DRIVE_PASSWORD = "networkDrivePassword";
    static final String MAIL_RECORDER_ENABLED = "mailRecorderEnabled";
    static final String EXPERIMENTAL_FEATURE_PREFIX = EXPERIMENTAL_FEATURE + ".";
    static final String WEB_ROOT = "webRoot";
    static final String USER_FILE_ROOT = "userFileRoot";
    static final String WEBFILES_ROOT_ENABLED = "webfilesEnabled";
    static final String FILE_UPLOAD_DISABLED = "fileUploadDisabled";
    static final String MAX_BLOB_SIZE = "maxBLOBSize";
    static final String EXT3_REQUIRED = "ext3Required";
    static final String EXT3API_REQUIRED = "ext3APIRequired";
    static final String SELF_REPORT_EXCEPTIONS = "selfReportExceptions";
    static final String USE_CONTAINER_RELATIVE_URL = "useContainerRelativeURL";
    static final String ALLOW_API_KEYS = "allowApiKeys";
    static final String API_KEY_EXPIRATION_SECONDS = "apiKeyExpirationSeconds";
    static final String ALLOW_SESSION_KEYS = "allowSessionKeys";
    static final String CSRF_CHECK = "CSRFCheck";
    static final String X_FRAME_OPTIONS = "XFrameOption";

    private static final String SERVER_GUID = "serverGUID";
    private static final String SERVER_GUID_XML_PARAMETER_NAME = "org.labkey.mothership." + SERVER_GUID;
    private static final String SITE_CONFIG_NAME = "SiteConfig";
    private static final String SERVER_SESSION_GUID = GUID.makeGUID();

    protected String getType()
    {
        return "site settings";
    }

    @Override
    protected boolean isPasswordProperty(String propName)
    {
        return super.isPasswordProperty(propName) || NETWORK_DRIVE_PASSWORD.equals(propName);
    }

    protected String getGroupName()
    {
        return SITE_CONFIG_NAME;
    }

    public String getContextPath()
    {
        if (_contextPathStr == null)
        {
            throw new IllegalStateException("Context path should have been set in ModuleLoader.doInit()");
        }
        return _contextPathStr;
    }

    public Path getParsedContextPath()
    {
        return _contextPath;
    }

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

    public int getServerPort()
    {
        return getBaseServerProperties().getServerPort();
    }

    public String getScheme()
    {
        return getBaseServerProperties().getScheme();
    }

    public String getServerName()
    {
        return getBaseServerProperties().getServerName();
    }

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

    public boolean isSetBaseServerUrl()
    {
        return null != getBaseServerUrl();
    }

    public String getBaseServerUrl()
    {
        return lookupStringValue(BASE_SERVER_URL_PROP, null);
    }

    public String getDefaultDomain()
    {
        return lookupStringValue(DEFAULT_DOMAIN_PROP, "");
    }

    // CONSIDER: All the following should probably be migrated into look & feel settings, making them overrideable at the project level

    public String getHomePageUrl()
    {
        return getHomePageActionURL().getLocalURIString();
    }

    public ActionURL getHomePageActionURL()
    {
        //noinspection ConstantConditions
        return PageFlowUtil.urlProvider(ProjectUrls.class).getHomeURL();
    }

    public String getSiteWelcomePageUrlString()
    {
        return StringUtils.trimToNull(LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getCustomWelcome());
    }

    public int getLookAndFeelRevision()
    {
        return lookupIntValue(LOOK_AND_FEEL_REVISION, 0);
    }

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

    public String getPipelineToolsDirectory()
    {
        @SuppressWarnings("ConstantConditions")
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
        return lookupIntValue(MAX_BLOB_SIZE, 50_000_000);
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
            return ExceptionReportingLevel.valueOf(lookupStringValue(EXCEPTION_REPORTING_LEVEL, isDevMode() ? ExceptionReportingLevel.NONE.toString() : ExceptionReportingLevel.MEDIUM.toString()));
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
            try
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

    public String getBLASTServerBaseURL()
    {
        return lookupStringValue(BLAST_SERVER_BASE_URL_PROP, "http://www.ncbi.nlm.nih.gov/blast/Blast.cgi?CMD=Web&amp;LAYOUT=TwoWindows&amp;AUTO_FORMAT=Semiauto&amp;ALIGNMENTS=50&amp;ALIGNMENT_VIEW=Pairwise&amp;CDD_SEARCH=on&amp;CLIENT=web&amp;COMPOSITION_BASED_STATISTICS=on&amp;DATABASE=nr&amp;DESCRIPTIONS=100&amp;ENTREZ_QUERY=(none)&amp;EXPECT=1000&amp;FILTER=L&amp;FORMAT_OBJECT=Alignment&amp;FORMAT_TYPE=HTML&amp;I_THRESH=0.005&amp;MATRIX_NAME=BLOSUM62&amp;NCBI_GI=on&amp;PAGE=Proteins&amp;PROGRAM=blastp&amp;SERVICE=plain&amp;SET_DEFAULTS.x=41&amp;SET_DEFAULTS.y=5&amp;SHOW_OVERVIEW=on&amp;END_OF_HTTPGET=Yes&amp;SHOW_LINKOUT=yes&amp;QUERY=");
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
            _enlistmentId = ModuleLoader.getInstance().loadEnlistmentId(new File(_projectRoot));
    }

    @Nullable
    public String getProjectRoot()
    {
        return _projectRoot;
    }

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

    @NotNull
    public UsageReportingLevel getUsageReportingLevel()
    {
        try
        {
            return UsageReportingLevel.valueOf(lookupStringValue(USAGE_REPORTING_LEVEL, isDevMode() ? UsageReportingLevel.NONE.toString() : UsageReportingLevel.MEDIUM.toString()));
        }
        catch (IllegalArgumentException e)
        {
            return UsageReportingLevel.LOW;
        }
    }

    // Get the name of the webapp configuration file, e.g., labkey.xml, cpas.xml, or ROOT.xml.  Used in some error messages
    //  to provide suggestions to the admin.
    public String getWebappConfigurationFilename()
    {
        String path = getContextPath();

        return "".equals(path) ? "ROOT.xml" : path.substring(1) + ".xml";
    }

    @Override
    public String getAdministratorContactEmail()
    {
        String defaultValue = null;
        // Default to the oldest site administrator's email address
        List<Pair<Integer, String>> members = SecurityManager.getGroupMemberNamesAndIds("Administrators");
        // Sort to find the minimum user id
        members.sort(Comparator.comparing(Pair::getKey));
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

    @Override
    public String getAdministratorContactHTML()
    {
        return "Please <a href=\"mailto:" + PageFlowUtil.filter(getAdministratorContactEmail()) + "\">contact a system administrator</a>";
    }

    // TODO: Ditch this in favor of Constants.getPreviousReleaseVersion()?
    public String getLabKeyVersionString()
    {
        DecimalFormat format = new DecimalFormat("0.00");
        return format.format(ModuleLoader.getInstance().getCoreModule().getVersion());
    }

    @Override
    public boolean getUseContainerRelativeURL()
    {
        return lookupBooleanValue(USE_CONTAINER_RELATIVE_URL, true);
    }

    @Override
    public boolean isSetUseContainerRelativeURL()
    {
        return null != lookupStringValue(USE_CONTAINER_RELATIVE_URL, null);
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
    public String getCSRFCheck()
    {
        return lookupStringValue(CSRF_CHECK, "ADMINONLY");
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
        final boolean isBootstrap = ModuleLoader.getInstance().isNewInstall();

        // populate site settings with values from startup configuration as appropriate for prop modifier and isBootstrap flag
        // expects startup properties formatted like: SiteSettings.sslRequired;bootstrap=True
        // for a list of recognized site setting properties refer to: AppPropsImpl.java
        Collection<ConfigProperty> startupProps = ModuleLoader.getInstance().getConfigProperties(ConfigProperty.SCOPE_SITE_SETTINGS);
        if (startupProps.isEmpty())
            return;
        WriteableAppProps writeable = AppProps.getWriteableInstance();
        startupProps
            .forEach(prop -> {
                if (prop.getModifier() == ConfigProperty.modifier.startup || (isBootstrap && prop.getModifier() == ConfigProperty.modifier.bootstrap))
                {
                    writeable.storeStringValue(prop.getName(), prop.getValue());
                }
            });
        writeable.save(null);
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

}
