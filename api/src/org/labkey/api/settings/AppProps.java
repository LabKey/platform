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
package org.labkey.api.settings;

import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.SupportedDatabase;
import org.labkey.api.util.ExceptionReportingLevel;
import org.labkey.api.util.Path;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.view.ActionURL;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores basic site-wide configuration.
 * @see org.labkey.api.settings.WriteableAppProps
 */
public interface AppProps
{
    AppProps _instance = new AppPropsImpl();

    String SCOPE_SITE_SETTINGS = "SiteSettings";

    // Used for all optional features; "experimental" for historical reasons.
    String OPTIONAL_FEATURE_PREFIX = "experimentalFeature.";
    String SCOPE_OPTIONAL_FEATURE = "ExperimentalFeature"; // Startup property prefix for all optional features; "Experimental" for historical reasons.
    String EXPERIMENTAL_NO_GUESTS = "disableGuestAccount";
    String EXPERIMENTAL_BLOCKER = "blockMaliciousClients";
    String EXPERIMENTAL_RESOLVE_PROPERTY_URI_COLUMNS = "resolve-property-uri-columns";
    String ADMIN_PROVIDED_ALLOWED_EXTERNAL_RESOURCES = "allowedExternalResources";
    String QUANTITY_COLUMN_SUFFIX_TESTING = "quantityColumnSuffixTesting";
    String REJECT_CONTROLLER_FIRST_URLS = "rejectControllerFirstUrls";
    String MULTI_VALUE_TEXT_CHOICE = "multiChoiceDataType";

    String UNKNOWN_VERSION = "Unknown Release Version";

    static AppProps getInstance()
    {
        return _instance;
    }

    static WriteableAppProps getWriteableInstance()
    {
        return new WriteableAppProps(ContainerManager.getRoot());
    }

    String getServerSessionGUID();

    boolean isMailRecorderEnabled();

    boolean isOptionalFeatureEnabled(String feature);

    boolean isDevMode();

    @Nullable
    String getEnlistmentId();

    boolean isCachingAllowed();

    boolean isRecompileJspEnabled();

    /**
     * Indicates whether modules' "sourcePath" and "buildPath" values be ignored. This allows a server to run in devMode
     * without the risk of loading unwanted resources from a source tree that may not match the deployed server.
     *
     * <strong>WARNING</strong>: Setting this flag will interfere with the population of module beans, resulting in a
     * mismatch between deployed modules and their properties on the server.
     *
     * @return value of the 'labkey.ignoreModuleSource' system property. Defaults to <code>false</code>
     *
     * @see org.labkey.api.module.DefaultModule#setSourcePath(String)
     * @see org.labkey.api.module.DefaultModule#setBuildPath(String)
     * @see DefaultModule#computeResourceDirectory()
     */
    boolean isIgnoreModuleSource();

    void setProjectRoot(String projectRoot);

    /**
     * @return the root of the main source tree
     */
    @Nullable
    String getProjectRoot();

    /**
     * @return directory under which all containers will automatically have their own subdirectory for storing files
     */
    @Nullable
    File getFileSystemRoot();

    @NotNull
    UsageReportingLevel getUsageReportingLevel();

    /**
     * Returns the core module's release version, a string such as "20.3-SNAPSHOT", "20.1.0", or "20.3.7".
     * Or "Unknown Release Version".
     */
    @NotNull
    String getReleaseVersion();

    /**
     * Convenience method for getting the core schema version, returning 0.0 instead of null
     */
    double getSchemaVersion();

    String getContextPath();

    Path getParsedContextPath();

    int getServerPort();

    String getScheme();

    String getServerName();

    /**
     * Save the current request URL if the base server URL property is not set
     */
    void ensureBaseServerUrl(HttpServletRequest request);

    void setContextPath(String contextPath);

    boolean isSetBaseServerUrl();

    String getBaseServerUrl();

    String getHomePageUrl();

    ActionURL getHomePageActionURL();

    String getSiteWelcomePageUrlString();

    int getLookAndFeelRevision();

    String getDefaultLsidAuthority();

    String getPipelineToolsDirectory();

    boolean isSSLRequired();

    boolean isUserRequestedAdminOnlyMode();

    String getAdminOnlyMessage();

    boolean isShowRibbonMessage();

    @Nullable String getRibbonMessage();

    int getSSLPort();

    int getMemoryUsageDumpInterval();

    /** Timeout in seconds for read-only HTTP requests, after which resources like DB connections and spawned processes will be killed. Set to 0 to disable. */
    int getReadOnlyHttpRequestTimeout();

    int getMaxBLOBSize();

    boolean isExt3Required();

    boolean isExt3APIRequired();

    ExceptionReportingLevel getExceptionReportingLevel();

    /**
     * Flag specifying if the project navigation access is open/closed. Open (default) means users will see the full
     * folder tree for all folders they have permissions to see. Closed follows the rules as specified in issue #32718.
     *
     * @return if navigation access is open
     */
    boolean isNavigationAccessOpen();

    boolean isSelfReportExceptions();

    String getServerGUID();

    String getBLASTServerBaseURL();

    /** @return the name of the Tomcat XML deployment descriptor based on the context path for this install - now always application.properties */
    String getWebappConfigurationFilename();

    /**
     * Email address of the primary site or application administrator, set on the site settings page. Useful in error
     * messages when only an administrator can help. Returns null if there are no site or application admins (i.e.,
     * only impersonating troubleshooters).
     *
     * @return Email address of the primary site or application administrator
     */
    @Nullable String getAdministratorContactEmail(boolean includeAppAdmins);

    boolean isAllowApiKeys();

    int getApiKeyExpirationSeconds();

    boolean isAllowSessionKeys();

    // configurable http security settings

    /**
     * @return "SAMEORIGIN" or "DENY" or "ALLOW"
     */
    String getXFrameOption();

    String getStaticFilesPrefix();

    boolean isWebfilesRootEnabled();

    boolean isFileUploadDisabled();

    boolean isInvalidFilenameUploadBlocked();

    boolean isInvalidFilenameBlocked();

    /** @return whether the server should include its name and version as a header in HTTP responses */
    boolean isIncludeServerHttpHeader();

    /**
     * @return List of configured external redirect hosts
     */
    @NotNull
    List<String> getExternalRedirectHosts();

    /**
     * @return List of configured external resource hosts
     */
    @Deprecated // Left for upgrade code only
    @NotNull
    List<String> getExternalSourceHosts();

    Map<StashedStartupProperties, StartupPropertyEntry> getStashedStartupProperties();

    @NotNull String getDistributionName();

    @NotNull String getDistributionFilename();

    @NotNull Set<SupportedDatabase> getDistributionSupportedDatabases();

    @NotNull List<String> getAllowedExtensions();

    @NotNull String getAllowedExternalResourceHosts();
}
