/*
 * Copyright (c) 2005-2015 Fred Hutchinson Cancer Research Center
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

/**
 * Stores basic site-wide configuration.
 * @see org.labkey.api.settings.WriteableAppProps
 *
 * User: arauch
 * Date: Apr 11, 2005
 */
public class AppProps
{
    private static final Interface _instance = new AppPropsImpl();

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
        String getMascotUserPassword();
        String getMascotHTTPProxy();
        String getNetworkDriveLetter();
        String getNetworkDrivePath();
        String getNetworkDriveUser();
        String getNetworkDrivePassword();
        String getServerSessionGUID();
        boolean isMailRecorderEnabled();
        boolean isExperimentalFeatureEnabled(String feature);
        boolean isDevMode();
        @Nullable String getEnlistmentId();
        boolean isCachingAllowed();
        boolean isRecompileJspEnabled();
        void setProjectRoot(String projectRoot);
        String getProjectRoot();
        File getFileSystemRoot();
        UsageReportingLevel getUsageReportingLevel();
        String getLabKeyVersionString();
        String getContextPath();
        Path getParsedContextPath();
        int getServerPort();
        String getScheme();
        String getServerName();
        void initializeFromRequest(HttpServletRequest request);
        void setContextPath(String contextPath);

        String getDefaultDomain();
        String getBaseServerUrl();
        String getHomePageUrl();
        ActionURL getHomePageActionURL();
        int getLookAndFeelRevision();
        String getDefaultLsidAuthority();
        String getPipelineToolsDirectory();
        boolean isSSLRequired();
        boolean isUserRequestedAdminOnlyMode();
        String getAdminOnlyMessage();
        boolean isShowRibbonMessage();
        String getRibbonMessageHtml();
        int getSSLPort();
        int getMemoryUsageDumpInterval();
        int getMaxBLOBSize();
        boolean isExt3Required();
        boolean isExt3APIRequired();
        ExceptionReportingLevel getExceptionReportingLevel();
        boolean isSelfReportExceptions();
        String getServerGUID();
        String getBLASTServerBaseURL();
        boolean hasMascotServer();
        String getMascotServer();
        String getMascotUserAccount();
        String getWebappConfigurationFilename();
        String getAdministratorContactEmail();
        boolean getUseContainerRelativeURL();
        boolean isTeamCityEnvironment();
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
