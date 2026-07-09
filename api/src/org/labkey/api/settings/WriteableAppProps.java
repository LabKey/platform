/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.ExceptionReportingLevel;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.view.NavTreeManager;

import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Objects;

import static org.labkey.api.settings.SiteSettingsProperties.*;
import static org.labkey.api.settings.RandomStartupProperties.*;

/**
 * A mutable version of {@link AppProps}.
 */
public class WriteableAppProps extends AppPropsImpl
{
    private final Container _container;

    // Issue 53562: Cap max blob size
    public static final int SOFT_MAX_BLOB_SIZE = 200 * 1024 * 1024;

    public WriteableAppProps(Container c)
    {
        _container = c;
        makeWriteable(c);
    }

    /** Override to make public and collect user for auditing purposes */
    public void save(@Nullable User user)
    {
        super.save();
        writeAuditLogEvent(_container, user);

        if (!Objects.equals(getOldProperties().get(navAccessOpen.name()), getProperties().get(navAccessOpen.name())))
            NavTreeManager.uncacheAll();
    }

    public void setAdminOnlyMessage(String message)
    {
        storeStringValue(adminOnlyMessage, message);
    }

    public void setRibbonMessage(String messageHtml)
    {
        storeStringValue(ribbonMessage, messageHtml);
    }

    public void setShowRibbonMessage(boolean show)
    {
        storeBooleanValue(showRibbonMessage, show);
    }

    public void setSSLPort(int port)
    {
        if (port < 0 || port > 65535)
            throw new IllegalArgumentException("Invalid port number: " + port);
        storeIntValue(sslPort, port);
    }

    public void setMemoryUsageDumpInterval(int interval)
    {
        if (interval < 0)
            throw new IllegalArgumentException("memoryUsageDumpInterval must be >= 0");
        storeIntValue(memoryUsageDumpInterval, interval);
    }

    public void setReadOnlyHttpRequestTimeout(int timeout)
    {
        if (timeout < 0)
            throw new IllegalArgumentException("readOnlyHttpRequestTimeout must be >= 0");
        storeIntValue(readOnlyHttpRequestTimeout, timeout);
    }

    public void setMaxBLOBSize(int maxSize)
    {
        if (maxSize < 0)
            throw new IllegalArgumentException("maxBLOBSize must be >= 0");
        storeIntValue(maxBLOBSize, maxSize);
    }

    public void setSelfReportExceptions(boolean selfReport)
    {
        storeBooleanValue(selfReportExceptions, selfReport);
    }

    public void setBLASTServerBaseURL(String blastServerBaseURL)
    {
        storeStringValue(BLASTBaseURL, blastServerBaseURL);
    }

    public void setExceptionReportingLevel(ExceptionReportingLevel level)
    {
        storeStringValue(exceptionReportingLevel, level.toString());
    }

    public void setUsageReportingLevel(UsageReportingLevel level)
    {
        storeStringValue(usageReportingLevel, level.toString());
    }

    public void setBaseServerUrl(String url) throws URISyntaxException
    {
        // Strip trailing slashes to avoid double slashes in generated links
        if(url.endsWith("/"))
            url = url.substring(0, url.length() - 1);
        validateBaseServerUrl(url);

        storeStringValue(baseServerURL, url);
    }

    public void setPipelineToolsDir(String toolsDir)
    {
        storeStringValue(pipelineToolsDirectory, toolsDir);
    }

    public void setSSLRequired(boolean required)
    {
        storeBooleanValue(sslRequired, required);
    }

    public void setUserRequestedAdminOnlyMode(boolean mode)
    {
        storeBooleanValue(adminOnlyMode, mode);
    }

    public void setNavAccessOpen(boolean open)
    {
        storeBooleanValue(navAccessOpen, open);
    }

    public void setMailRecorderEnabled(boolean enabled)
    {
        storeBooleanValue(mailRecorderEnabled, enabled);
    }

    public void setFileSystemRoot(String root)
    {
        storeStringValue(siteFileRoot, root);
    }

    public void setWebfilesEnabled(boolean b)
    {
        storeBooleanValue(webfilesEnabled, b);
    }

    public void setFileUploadDisabled(boolean b)
    {
        storeBooleanValue(fileUploadDisabled, b);
    }

    public void setInvalidFilenameUploadBlocked(boolean b)
    {
        storeBooleanValue(invalidFilenameUploadBlocked, b);
    }

    public void setInvalidFilenameBlocked(boolean b)
    {
        storeBooleanValue(invalidFilenameBlocked, b);
    }

    public void setIncludeServerHttpHeader(boolean b)
    {
        storeBooleanValue(includeServerHttpHeader, b);
    }

    public void setTermsOfUseFrequencySeconds(int seconds)
    {
        if (seconds < 0)
            throw new IllegalArgumentException("termsOfUseFrequencySeconds must be >= 0");
        storeIntValue(termsOfUseFrequencySeconds, seconds);
    }

    public void setAdministratorContactEmail(String email)
    {
        storeStringValue(administratorContactEmail, email);
    }

    private void incrementLookAndFeelRevision()
    {
        storeIntValue(LOOK_AND_FEEL_REVISION, getLookAndFeelRevision() + 1);
    }

    public static void incrementLookAndFeelRevisionAndSave()
    {
        WriteableAppProps app = AppProps.getWriteableInstance();
        app.incrementLookAndFeelRevision();
        app.save(null);
    }

    public void setAllowApiKeys(boolean b)
    {
        storeBooleanValue(allowApiKeys, b);
    }

    public void setApiKeyExpirationSeconds(int seconds)
    {
        storeIntValue(apiKeyExpirationSeconds, seconds);
    }

    public void setAllowSessionKeys(boolean b)
    {
        storeBooleanValue(allowSessionKeys, b);
    }

    public void setExternalRedirectHosts(@NotNull Collection<String> externalRedirectHosts)
    {
        setAllowList(externalRedirectHostURLs, externalRedirectHosts);
    }

    private void setAllowList(RandomStartupProperties propName, @NotNull Collection<String> externalSourceHosts)
    {
        storeStringValue(propName, String.join(ALLOW_LIST_DELIMITER, externalSourceHosts));
    }

    public void setAllowedFileExtensions(Collection<String> allowedFileExtensions)
    {
        setAllowList(RandomStartupProperties.allowedFileExtensions, allowedFileExtensions);
        FileUtil.clearExtensionChecker();
    }

    public void setAllowedExternalResourceHosts(String jsonArray)
    {
        storeStringValue(ADMIN_PROVIDED_ALLOWED_EXTERNAL_RESOURCES, jsonArray);
    }

    public void setFeatureEnabled(String feature, boolean enabled)
    {
        storeBooleanValue(OPTIONAL_FEATURE_PREFIX + feature, enabled);
    }
}
