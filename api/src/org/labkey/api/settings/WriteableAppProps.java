/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.ExceptionReportingLevel;
import org.labkey.api.util.UsageReportingLevel;

import java.net.URISyntaxException;

/**
 * A mutable version of {@link AppProps}.
 * User: jeckels
 * Date: Dec 6, 2006
 */
public class WriteableAppProps extends AppPropsImpl
{
    private Container _container;

    public WriteableAppProps(Container c)
    {
        super();
        _container = c;
        makeWriteable(c);
    }

    /** Override to make public and collect user for auditing purposes */
    public void save(@Nullable User user)
    {
        super.save();
        writeAuditLogEvent(_container, user);
    }

    public void setAdminOnlyMessage(String adminOnlyMessage)
    {
        storeStringValue(ADMIN_ONLY_MESSAGE, adminOnlyMessage);
    }

    public void setRibbonMessageHtml(String messageHtml)
    {
        storeStringValue(RIBBON_MESSAGE, messageHtml);
    }

    public void setShowRibbonMessage(boolean show)
    {
        storeBooleanValue(SHOW_RIBBON_MESSAGE, show);
    }

    public void setSSLPort(int sslPort)
    {
        storeIntValue(SSL_PORT, sslPort);
    }

    public void setMemoryUsageDumpInterval(int memoryUsageDumpInterval)
    {
        storeIntValue(MEMORY_USAGE_DUMP_INTERVAL, memoryUsageDumpInterval);
    }

    public void setMaxBLOBSize(int maxBLOBSize)
    {
        storeIntValue(MAX_BLOB_SIZE, maxBLOBSize);
    }

    public void setSelfReportExceptions(boolean selfReportExceptions)
    {
        storeBooleanValue(SELF_REPORT_EXCEPTIONS, selfReportExceptions);
    }

    public void setExt3Required(boolean ext3Required)
    {
        storeBooleanValue(EXT3_REQUIRED, ext3Required);
    }

    public void setExt3APIRequired(boolean ext3APIRequired)
    {
        storeBooleanValue(EXT3API_REQUIRED, ext3APIRequired);
    }

    public void setBLASTServerBaseURL(String blastServerBaseURL)
    {
        storeStringValue(BLAST_SERVER_BASE_URL_PROP, blastServerBaseURL);
    }

    public void setExceptionReportingLevel(ExceptionReportingLevel level)
    {
        storeStringValue(EXCEPTION_REPORTING_LEVEL, level.toString());
    }

    public void setUsageReportingLevel(UsageReportingLevel level)
    {
        storeStringValue(USAGE_REPORTING_LEVEL, level.toString());
    }

    public void setDefaultDomain(String defaultDomain)
    {
        storeStringValue(DEFAULT_DOMAIN_PROP, defaultDomain);
    }

    public void setBaseServerUrl(String baseServerUrl) throws URISyntaxException
    {
        // Strip trailing slashes to avoid double slashes in generated links
        if(baseServerUrl.endsWith("/"))
            baseServerUrl = baseServerUrl.substring(0, baseServerUrl.length() - 1);
        validateBaseServerUrl(baseServerUrl);

        storeStringValue(BASE_SERVER_URL_PROP, baseServerUrl);
    }

    public void setPipelineToolsDir(String toolsDir)
    {
        storeStringValue(PIPELINE_TOOLS_DIR_PROP, toolsDir);
    }

    public void setSSLRequired(boolean sslRequired)
    {
        storeBooleanValue(SSL_REQUIRED, sslRequired);
    }

    public void setUserRequestedAdminOnlyMode(boolean adminOnlyMode)
    {
        storeBooleanValue(USER_REQUESTED_ADMIN_ONLY_MODE, adminOnlyMode);
    }

    public void setNetworkDriveLetter(String letter)
    {
        storeStringValue(NETWORK_DRIVE_LETTER, letter);
    }

    public void setNetworkDrivePath(String path)
    {
        storeStringValue(NETWORK_DRIVE_PATH, path);
    }

    public void setNetworkDriveUser(String user)
    {
        storeStringValue(NETWORK_DRIVE_USER, user);
    }

    public void setNetworkDrivePassword(String password)
    {
        storeStringValue(NETWORK_DRIVE_PASSWORD, password);
    }

    public void setMailRecorderEnabled(boolean enabled)
    {
        storeBooleanValue(MAIL_RECORDER_ENABLED, enabled);        
    }

    public void setFileSystemRoot(String root)
    {
        storeStringValue(WEB_ROOT, root);
    }

    public void setUserFilesRoot(String root)
    {
        storeStringValue(USER_FILE_ROOT, root);
    }

    public void setWebfilesEnabled(boolean b)
    {
        storeBooleanValue(WEBFILES_ROOT_ENABLED, b);
    }

    public void setFileUploadDisabled(boolean b)
    {
        storeBooleanValue(FILE_UPLOAD_DISABLED, b);
    }

    public void setAdministratorContactEmail(String email)
    {
        storeStringValue(ADMINISTRATOR_CONTACT_EMAIL, email);
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

    public void setUseContainerRelativeURL(boolean b)
    {
        storeBooleanValue(USE_CONTAINER_RELATIVE_URL, b);
    }

    public void setAllowApiKeys(boolean b)
    {
        storeBooleanValue(ALLOW_API_KEYS, b);
    }

    public void setApiKeyExpirationSeconds(int seconds)
    {
        storeIntValue(API_KEY_EXPIRATION_SECONDS, seconds);
    }

    public void setAllowSessionKeys(boolean b)
    {
        storeBooleanValue(ALLOW_SESSION_KEYS, b);
    }

    public void setXFrameOptions(String option)
    {
        storeStringValue(X_FRAME_OPTIONS, option);
    }

    public void setCSRFCheck(String check)
    {
        storeStringValue(CSRF_CHECK, check);
    }
}
