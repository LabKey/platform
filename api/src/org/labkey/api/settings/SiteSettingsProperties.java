/*
 * Copyright (c) 2022-2026 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.labkey.api.util.ExceptionReportingLevel;
import org.labkey.api.util.SafeToRenderEnum;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.util.logging.LogHelper;

import java.net.URISyntaxException;
import java.util.Arrays;

// Site settings constants are defined here in the same order as on the site settings page
public enum SiteSettingsProperties implements StartupProperty, SafeToRenderEnum
{
    administratorContactEmail("Primary site administrator")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setAdministratorContactEmail(value);
        }
    },
    baseServerURL("Base server URL (used to create links in emails sent by the system)")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            try
            {
                writeable.setBaseServerUrl(value);
            }
            catch (URISyntaxException e)
            {
                throw new IllegalArgumentException("Invalid URI for property " + name() + ": " + value, e);
            }
        }
    },
    usageReportingLevel("Check for updates and report usage statistics to the LabKey team. Valid values: " + Arrays.toString(UsageReportingLevel.values()))
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setUsageReportingLevel(UsageReportingLevel.valueOf(value));
        }
    },
    exceptionReportingLevel("Report exceptions to the LabKey team. Valid values: " + Arrays.toString(ExceptionReportingLevel.values()))
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setExceptionReportingLevel(ExceptionReportingLevel.valueOf(value));
        }
    },
    selfReportExceptions("Report exceptions to the local server")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setSelfReportExceptions(Boolean.parseBoolean(value));
        }
    },
    memoryUsageDumpInterval("Log memory usage frequency in minutes, for debugging. Set to 0 to disable.")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setMemoryUsageDumpInterval(Integer.parseInt(value));
        }
    },
    readOnlyHttpRequestTimeout("Timeout in seconds for read-only HTTP requests, after which resources like DB connections and spawned processes will be killed. Set to 0 to disable.")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setReadOnlyHttpRequestTimeout(Integer.parseInt(value));
        }
    },
    scriptExecutionTimeout("Timeout in seconds for server-side JavaScript such as trigger scripts. Measured in wall-clock time, including database and other Java operations invoked by the script. Set to 0 to disable.")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setScriptExecutionTimeout(Integer.parseInt(value));
        }
    },
    maxBLOBSize("Maximum file size, in bytes, to allow in database BLOBs")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setMaxBLOBSize(Integer.parseInt(value));
        }
    },
    sslRequired("Require SSL connections (users must connect via SSL)")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setSSLRequired(Boolean.parseBoolean(value));
        }
    },
    sslPort("SSL port number (specified in server config file)")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setSSLPort(Integer.parseInt(value));
        }
    },
    allowApiKeys("Let users create API keys")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setAllowApiKeys(Boolean.parseBoolean(value));
        }
    },
    apiKeyExpirationSeconds("API key expiration in seconds. -1 represents no expiration.")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setApiKeyExpirationSeconds(Integer.parseInt(value));
        }
    },
    allowSessionKeys("Let users create session keys, which are associated with the user's currents server session")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setAllowSessionKeys(Boolean.parseBoolean(value));
        }
    },
    pipelineToolsDirectory("Semicolon-separated list of directories on the web server containing executables that are run for pipeline jobs (e.g. TPP or XTandem)")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setPipelineToolsDir(value);
        }
    },
    showRibbonMessage("Display ribbon bar message")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setShowRibbonMessage(Boolean.parseBoolean(value));
        }
    },
    ribbonMessage("Ribbon bar message HTML")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setRibbonMessage(value);
        }
    },
    adminOnlyMode("Admin only mode (only site admins may log in)")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setUserRequestedAdminOnlyMode(Boolean.parseBoolean(value));
        }
    },
    adminOnlyMessage("Message to users when site is in admin-only mode (Wiki formatting allowed)")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setAdminOnlyMessage(value);
        }
    },
    navAccessOpen("Always include inaccessible parent folders in project menu when child folder is accessible")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setNavAccessOpen(Boolean.parseBoolean(value));
        }
    },
    includeServerHttpHeader("If set to false, do not include a 'Server' header in HTTP responses")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setIncludeServerHttpHeader(Boolean.parseBoolean(value));
        }
    },
    termsOfUseFrequencySeconds("Require terms-of-use acceptance frequency in seconds. 0 = every sign-in, or positive number of seconds between required re-acceptance.")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setTermsOfUseFrequencySeconds(Integer.parseInt(value));
        }
    };

    private final static Logger LOG = LogHelper.getLogger(SiteSettingsProperties.class, "Warnings about setting properties");

    private final String _description;

    SiteSettingsProperties(String description)
    {
        _description = description;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    public abstract void setValue(WriteableAppProps writeable, String value);
}
