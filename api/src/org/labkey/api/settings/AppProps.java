/*
 * Copyright (c) 2005-2014 Fred Hutchinson Cancer Research Center
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.util.ExceptionReportingLevel;
import org.labkey.api.util.Path;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.net.URISyntaxException;

/**
 * User: arauch
 * Date: Apr 11, 2005
 * Time: 1:10:18 PM
 */
public class AppProps
{
    private static Interface _instance = new AppPropsImpl();

    public static Interface getInstance()
    {
        return _instance;
    }

    public static WriteableAppProps getWriteableInstance()
    {
        return new WriteableAppProps(ContainerManager.getRoot());
    }

    public interface Interface
    {
        public String getMascotUserPassword();
        public String getMascotHTTPProxy();
        public String getNetworkDriveLetter();
        public String getNetworkDrivePath();
        public String getNetworkDriveUser();
        public String getNetworkDrivePassword();
        public String getServerSessionGUID();
        public boolean isMailRecorderEnabled();
        public boolean isExperimentalFeatureEnabled(String feature);
        public boolean isDevMode();
        public @Nullable String getEnlistmentId();
        public boolean isCachingAllowed();
        public boolean isRecompileJspEnabled();
        public void setProjectRoot(String projectRoot);
        public String getProjectRoot();
        public File getFileSystemRoot();
        public UsageReportingLevel getUsageReportingLevel();
        public String getLabKeyVersionString();
        public String getContextPath();
        public Path getParsedContextPath();
        public int getServerPort();
        public String getScheme();
        public String getServerName();
        public boolean isBaseServerUrlInitialized();
        public void initializeFromRequest(HttpServletRequest request);
        public void setBaseServerUrlAttributes(String baseServerUrl) throws URISyntaxException;
        public HttpServletRequest createMockRequest();
        public String getDefaultDomain();
        public String getBaseServerUrl();
        public String getHomePageUrl();
        public ActionURL getHomePageActionURL();
        public int getLookAndFeelRevision();
        public String getDefaultLsidAuthority();
        public String getPipelineToolsDirectory();
        public boolean isSSLRequired();
        public boolean isUserRequestedAdminOnlyMode();
        public String getAdminOnlyMessage();
        public boolean isShowRibbonMessage();
        public String getRibbonMessageHtml();
        public int getSSLPort();
        public int getMemoryUsageDumpInterval();
        public int getMaxBLOBSize();
        public boolean isExt3Required();
        public boolean isExt3APIRequired();
        public ExceptionReportingLevel getExceptionReportingLevel();
        public String getServerGUID();
        public String getBLASTServerBaseURL();
        public boolean hasMascotServer();
        public String getMascotServer();
        public String getMascotUserAccount();
        public String getWebappConfigurationFilename();
        public String getAdministratorContactEmail();
        public boolean getUseContainerRelativeURL();
        public boolean isTeamCityEnviornment();
    }

    public static final String EXPERIMENTAL_CONTAINER_RELATIVE_URL = "containerRelativeURL";
    public static final String EXPERIMENTAL_JAVASCRIPT_MOTHERSHIP = "javascriptMothership";
    public static final String EXPERIMENTAL_JAVASCRIPT_SERVER = "javascriptErrorServerLogging";
    public static final String EXPERIMENTAL_RSERVE_REPORTING = "rserveReports";

    // For Customisable web colour theme
    protected static final String WEB_THEME_CONFIG_NAME = "WebThemeConfig";

    // For Customisable web colour theme
    public static PropertyManager.PropertyMap getWebThemeConfigProperties()
    {
        return PropertyManager.getWritableProperties(AppPropsImpl.SITE_CONFIG_USER, ContainerManager.getRoot(), WEB_THEME_CONFIG_NAME, true);
    }
}
