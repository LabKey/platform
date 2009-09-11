/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.util.ExceptionReportingLevel;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.UsageReportingLevel;

import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.Date;

/**
 * User: jeckels
 * Date: Dec 6, 2006
 */
public class WriteableAppProps extends AppProps
{
    public WriteableAppProps(Container c) throws SQLException
    {
        super();
        makeWriteable(c);
    }

    // Make public
    public void save() throws SQLException
    {
        super.save();
    }

    public void setAdminOnlyMessage(String adminOnlyMessage)
    {
        storeStringValue(ADMIN_ONLY_MESSAGE, adminOnlyMessage);
    }

    public void setSSLPort(int sslPort)
    {
        storeIntValue(SSL_PORT, sslPort);
    }

    public void setSystemMaintenanceInterval(String systemMaintenanceInterval)
    {
        storeStringValue(SYSTEM_MAINTENANCE_INTERVAL, systemMaintenanceInterval);
    }

    public void setSystemMaintenanceTime(Date time)
    {
        String parsedTime = SystemMaintenance.formatSystemMaintenanceTime(time);
        storeStringValue(SYSTEM_MAINTENANCE_TIME, parsedTime);
    }

    public void setMemoryUsageDumpInterval(int memoryUsageDumpInterval)
    {
        storeIntValue(MEMORY_USAGE_DUMP_INTERVAL, memoryUsageDumpInterval);
    }

    public void setMascotServer(String mascotServer)
    {
        storeStringValue(MASCOT_SERVER_PROP, mascotServer);
    }

    public void setMascotUserAccount(String mascotUserAccount)
    {
        storeStringValue(MASCOT_USERACCOUNT_PROP, mascotUserAccount);
    }

    public void setMascotUserPassword(String mascotUserPassword)
    {
        storeStringValue(MASCOT_USERPASSWORD_PROP, mascotUserPassword);
    }

    public void setMascotHTTPProxy(String mascotHTTPProxy)
    {
        storeStringValue(MASCOT_HTTPPROXY_PROP, mascotHTTPProxy);
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

    public void setDefaultLsidAuthority(String defaultLsidAuthority)
    {
        storeStringValue(DEFAULT_LSID_AUTHORITY_PROP, defaultLsidAuthority);
    }

    public void setBaseServerUrl(String baseServerUrl) throws URISyntaxException
    {
        setBaseServerUrlAttributes(baseServerUrl);

        storeStringValue(BASE_SERVER_URL_PROP, baseServerUrl);
    }

    public void setPipelineToolsDir(String toolsDir)
    {
        storeStringValue(PIPELINE_TOOLS_DIR_PROP, toolsDir);
    }

    /**
     * Sets the database prop.properties for the value of SequestServer
     * @param sequestServer   name of sequest HTTP server.
     */
    public void setSequestServer(String sequestServer)
    {
        storeStringValue(SEQUEST_SERVER_PROP, sequestServer);
    }

    public void setPipelineFTPHost(String pipelineFTPHost)
    {
        storeStringValue(PIPELINE_FTPHOST_PROP, pipelineFTPHost);
    }

    public void setPipelineFTPPort(String pipelineFTPPort)
    {
        storeStringValue(PIPELINE_FTPPORT_PROP, pipelineFTPPort);
    }

    public void setPipelineFTPSecure(boolean pipelineFTPSecure)
    {
        storeBooleanValue(PIPELINE_FTPSECURE_PROP, pipelineFTPSecure);
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

    public void setCaBIGEnabled(boolean enabled)
    {
        storeBooleanValue(CABIG_ENABLED, enabled);
    }

    public void setMailRecorderEnabled(boolean enabled)
    {
        storeBooleanValue(MAIL_RECORDER_ENABLED, enabled);        
    }

    public void setMicroarrayFeatureExtractionServer(String name)
    {
        storeStringValue(MICROARRAY_FEATURE_EXTRACTION_SERVER_PROP, name);
    }

    private void incrementLookAndFeelRevision()
    {
        storeIntValue(LOOK_AND_FEEL_REVISION, getLookAndFeelRevision() + 1);
    }

    public static void incrementLookAndFeelRevisionAndSave() throws SQLException
    {
        WriteableAppProps app = WriteableAppProps.getWriteableInstance();
        app.incrementLookAndFeelRevision();
        app.save();
    }
}
