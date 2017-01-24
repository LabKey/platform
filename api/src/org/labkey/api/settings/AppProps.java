/*
 * Copyright (c) 2005-2016 Fred Hutchinson Cancer Research Center
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

import org.jetbrains.annotations.NotNull;
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
        /** @return the root of the main source tree */
        @Nullable
        String getProjectRoot();
        /** @return directory under which all containers will automatically have their own subdirectory for storing files */
        @Nullable
        File getFileSystemRoot();
        /** @return directory under which user-specific files will be stored in a user-specific subdirectory */
        @Nullable
        File getUserFilesRoot();
        @NotNull
        UsageReportingLevel getUsageReportingLevel();
        String getLabKeyVersionString();
        String getContextPath();
        Path getParsedContextPath();
        int getServerPort();
        String getScheme();
        String getServerName();
        /** Save the current request URL if the base server URL property is not set */
        void ensureBaseServerUrl(HttpServletRequest request);
        void setContextPath(String contextPath);

        String getDefaultDomain();
        boolean isSetBaseServerUrl();
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
        String getWebappConfigurationFilename();

        /**
         * Email address of the primary site administrator, set on the site settings page. Useful in error messages when only a site administrator can help.
         * @return Email address of the primary site administrator
         */
        String getAdministratorContactEmail();

        /**
         * HTML to display when a user might need to contact a site administrator (e.g., for user creation problems, inactive user, etc.).
         * Currently hard-coded to return "Please contact a system administrator" with a mailto: link to getAdministratorContactEmail().
         * We may want to make this admin-configurable in the future.
         * @return HTML to display on error pages
         */
        String getAdministratorContactHTML();

        boolean getUseContainerRelativeURL();
        boolean isSetUseContainerRelativeURL();
        boolean isAllowSessionKeys();

        // configurable http security settings
        /** @return "POST" or "ADMINONLY" */
        String getCSRFCheck();
        /** @return "SAMEORIGIN" or "DENY" or "ALLOW" */
        String getXFrameOptions();
        String getStaticFilesPrefix();
    }

    public static final String EXPERIMENTAL_JAVASCRIPT_MOTHERSHIP = "javascriptMothership";
    public static final String EXPERIMENTAL_JAVASCRIPT_SERVER = "javascriptErrorServerLogging";
    public static final String EXPERIMENTAL_RSERVE_REPORTING = "rserveReports";
    public static final String EXPERIMENTAL_USER_FOLDERS = "userFolders";


    // For Customisable web colour theme
    protected static final String WEB_THEME_CONFIG_NAME = "WebThemeConfig";

    // For Customisable web colour theme
    public static PropertyManager.PropertyMap getWebThemeConfigProperties()
    {
        return PropertyManager.getWritableProperties(AppPropsImpl.SITE_CONFIG_USER, ContainerManager.getRoot(), WEB_THEME_CONFIG_NAME, true);
    }
}
