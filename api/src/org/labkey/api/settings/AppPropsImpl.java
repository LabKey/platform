/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: jeckels
 * Date: Jun 21, 2012
 */
public class AppPropsImpl extends AbstractWriteableSettingsGroup implements AppProps.Interface
{
    private String _contextPathStr;
    private Path _contextPath = null;
    private String _projectRoot = null;
    private String _enlistmentId = null;
    private String _initialRequestBaseServerUrl = null;

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
    protected static final String USE_CONTAINER_RELATIVE_URL = "useContainerRelativeURL";

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

    public void initializeFromRequest(HttpServletRequest request)
    {
        assert null == _initialRequestBaseServerUrl;

        // Stash this away just in case we need it.
        _initialRequestBaseServerUrl = URLHelper.getBaseServer(request.getScheme(), request.getServerName(), request.getServerPort()).toString();
    }


    public void validateBaseServerUrl(String baseServerUrl) throws URISyntaxException
    {
        BaseServerProperties.validate(baseServerUrl);
    }

    private BaseServerProperties getBaseServerProperties()
    {
        return BaseServerProperties.parse(getBaseServerUrl());
    }

    public String getBaseServerUrl()
    {
        String baseServerUrl = lookupStringValue(BASE_SERVER_URL_PROP, null);

        if (null == baseServerUrl)
        {
            try
            {
                BaseServerProperties.validate(_initialRequestBaseServerUrl);

                WriteableAppProps writeable = AppProps.getWriteableInstance();
                writeable.storeStringValue(BASE_SERVER_URL_PROP, _initialRequestBaseServerUrl);
                writeable.save();

                baseServerUrl = _initialRequestBaseServerUrl;
            }
            catch (URISyntaxException e)
            {
                throw new RuntimeException("Invalid initial request URL", e);
            }
        }

        return baseServerUrl;
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
            _enlistmentId = ModuleLoader.getInstance().loadEnlistmentId(new File(_projectRoot));
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
            return UsageReportingLevel.valueOf(lookupStringValue(USAGE_REPORTING_LEVEL, isDevMode() ? UsageReportingLevel.NONE.toString() : UsageReportingLevel.MEDIUM.toString()));
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
        Collections.sort(members, (o1, o2) -> o1.getKey().compareTo(o2.getKey()));
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
    public boolean isTeamCityEnvironment()
    {
        String buildConfName = System.getProperty("teamcity.build.id");
        return StringUtils.isNotBlank(buildConfName);
    }
}
