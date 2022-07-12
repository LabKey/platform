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
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerManager.RootContainerException;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.portal.ProjectUrls;
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
import org.labkey.api.util.logging.LogHelper;
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
import static org.labkey.api.settings.SiteSettingsProperties.*;
import static org.labkey.api.settings.RandomStartupProperties.*;

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

    static final String LOOK_AND_FEEL_REVISION = "logoRevision";
    static final String DEFAULT_LSID_AUTHORITY_PROP = "defaultLsidAuthority";
    static final String EXPERIMENTAL_FEATURE_PREFIX = EXPERIMENTAL_FEATURE + ".";
    static final String EXTERNAL_REDIRECT_HOST_DELIMITER = "\n";

    private static final String SITE_CONFIG_NAME = "SiteConfig";
    private static final String SERVER_GUID = "serverGUID";
    private static final String SERVER_GUID_XML_PARAMETER_NAME = "org.labkey.mothership." + SERVER_GUID;
    private static final String SERVER_SESSION_GUID = GUID.makeGUID();

    private static final Logger LOG = LogHelper.getLogger(AppPropsImpl.class, "Site settings startup properties");

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
                writeable.storeStringValue(baseServerURL, initialRequestBaseServerUrl);
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
        return lookupStringValue(baseServerURL, null);
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

        return lookupStringValue(pipelineToolsDirectory, binDir.getAbsolutePath());
    }

    @Override
    public boolean isSSLRequired()
    {
        return lookupBooleanValue(sslRequired, false);
    }

    @Override
    public boolean isUserRequestedAdminOnlyMode()
    {
        return lookupBooleanValue(adminOnlyMode, false);
    }

    @Override
    public String getAdminOnlyMessage()
    {
        return lookupStringValue(adminOnlyMessage, "This site is currently undergoing maintenance, and only administrators can log in.");
    }

    @Override
    public boolean isShowRibbonMessage()
    {
        return lookupBooleanValue(showRibbonMessage, false);
    }

    @Override
    public @Nullable String getRibbonMessage()
    {
        return lookupStringValue(ribbonMessage, null);
    }


    @Override
    public int getSSLPort()
    {
        return lookupIntValue(sslPort, 443);
    }

    @Override
    public int getMemoryUsageDumpInterval()
    {
        return lookupIntValue(memoryUsageDumpInterval, 0);
    }

    @Override
    public int getMaxBLOBSize()
    {
        return lookupIntValue(maxBLOBSize, 50_000_000);
    }

    @Override
    public boolean isExt3Required()
    {
        return lookupBooleanValue(ext3Required, false);
    }

    @Override
    public boolean isExt3APIRequired() { return lookupBooleanValue(ext3APIRequired, false); }

    @Override
    public boolean isSelfReportExceptions() { return MothershipReport.isShowSelfReportExceptions() && lookupBooleanValue(selfReportExceptions, true); }

    @Override
    public ExceptionReportingLevel getExceptionReportingLevel()
    {
        try
        {
            return ExceptionReportingLevel.valueOf(lookupStringValue(exceptionReportingLevel, isDevMode() ? ExceptionReportingLevel.NONE.toString() : ExceptionReportingLevel.MEDIUM.toString()));
        }
        catch (IllegalArgumentException e)
        {
            return ExceptionReportingLevel.LOW;
        }
    }

    @Override
    public boolean isNavigationAccessOpen()
    {
        return lookupBooleanValue(navAccessOpen, true);
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
        return lookupStringValue(BLASTBaseURL, "http://www.ncbi.nlm.nih.gov/blast/Blast.cgi?CMD=Web&amp;LAYOUT=TwoWindows&amp;AUTO_FORMAT=Semiauto&amp;ALIGNMENTS=50&amp;ALIGNMENT_VIEW=Pairwise&amp;CDD_SEARCH=on&amp;CLIENT=web&amp;COMPOSITION_BASED_STATISTICS=on&amp;DATABASE=nr&amp;DESCRIPTIONS=100&amp;ENTREZ_QUERY=(none)&amp;EXPECT=1000&amp;FILTER=L&amp;FORMAT_OBJECT=Alignment&amp;FORMAT_TYPE=HTML&amp;I_THRESH=0.005&amp;MATRIX_NAME=BLOSUM62&amp;NCBI_GI=on&amp;PAGE=Proteins&amp;PROGRAM=blastp&amp;SERVICE=plain&amp;SET_DEFAULTS.x=41&amp;SET_DEFAULTS.y=5&amp;SHOW_OVERVIEW=on&amp;END_OF_HTTPGET=Yes&amp;SHOW_LINKOUT=yes&amp;QUERY=");
    }

    @Override
    public String getServerSessionGUID()
    {
        return SERVER_SESSION_GUID;
    }

    @Override
    public boolean isMailRecorderEnabled()
    {
        return lookupBooleanValue(mailRecorderEnabled, false);
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
        String fileRoot = lookupStringValue(siteFileRoot, null);
        // Fall back to "webRoot", the old property name
        if (null == fileRoot)
            fileRoot = lookupStringValue("webRoot", "");
        if (!StringUtils.isEmpty(fileRoot))
        {
            return new File(fileRoot);
        }
        return null;
    }

    @Override
    @Nullable
    public File getUserFilesRoot()
    {
        String userRoot = lookupStringValue(userFileRoot, "");
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
            return UsageReportingLevel.valueOf(lookupStringValue(usageReportingLevel, isDevMode() ? UsageReportingLevel.NONE.toString() : UsageReportingLevel.ON.toString()));
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
        String result = lookupStringValue(administratorContactEmail, defaultValue);

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
        return lookupBooleanValue(useContainerRelativeURL, true);
    }

    @Override
    public boolean isAllowApiKeys()
    {
        return lookupBooleanValue(allowApiKeys, false);
    }

    @Override
    public int getApiKeyExpirationSeconds()
    {
        return lookupIntValue(apiKeyExpirationSeconds, -1);
    }

    @Override
    public boolean isAllowSessionKeys()
    {
        return lookupBooleanValue(allowSessionKeys, false);
    }

    @Override
    public String getXFrameOption()
    {
        return lookupStringValue(XFrameOption, "SAMEORIGIN");
    }

    @Override
    public String getStaticFilesPrefix()
    {
        // CURRENTLY SET using -Dstatic.files.prefix=//static.web.site.com
        // NOT IN UI, because one mistake will probably render the site unusable
        String s = System.getProperty("static.files.prefix");
        return trimToNull(s);
    }

    public static class SiteSettingsPropertyHandler extends StandardStartupPropertyHandler<SiteSettingsProperties>
    {
        public SiteSettingsPropertyHandler()
        {
            super(SCOPE_SITE_SETTINGS, SiteSettingsProperties.class);
        }

        @Override
        public void handle(Map<SiteSettingsProperties, StartupPropertyEntry> properties)
        {
            if (!properties.isEmpty())
            {
                WriteableAppProps writeable = AppProps.getWriteableInstance();
                properties.forEach((ssp, cp) -> {
                    LOG.info("Setting site settings startup property '" + ssp.name() + "' to '" + cp.getValue() + "'");
                    ssp.setValue(writeable, cp.getValue());
                });
                writeable.save(null);
            }
        }
    }

    public static void populateSiteSettingsWithStartupProps()
    {
        // populate site settings with values from startup configuration
        // expects startup properties formatted like: SiteSettings.sslRequired;bootstrap=True
        // for a list of recognized site setting properties see the "Available Site Settings" action

        ModuleLoader.getInstance().handleStartupProperties(new SiteSettingsPropertyHandler());

        ModuleLoader.getInstance().handleStartupProperties(new StandardStartupPropertyHandler<>(SCOPE_SITE_SETTINGS, RandomStartupProperties.class)
        {
            @Override
            public void handle(Map<RandomStartupProperties, StartupPropertyEntry> properties)
            {
                if (!properties.isEmpty())
                {
                    WriteableAppProps writeable = AppProps.getWriteableInstance();
                    properties.forEach((rsp, cp) -> {
                        LOG.info("Setting additional site-level startup property '" + rsp.name() + "' to '" + cp.getValue() + "'");
                        rsp.setValue(writeable, cp.getValue());
                    });
                    writeable.save(null);
                }
            }
        });

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
        return lookupBooleanValue(webfilesEnabled, false);
    }

    @Override
    public boolean isFileUploadDisabled()
    {
        return lookupBooleanValue(fileUploadDisabled, false);
    }

    @Override
    @NotNull
    public List<String> getExternalRedirectHosts()
    {
        String urls = lookupStringValue(externalRedirectHostURLs, "");
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
