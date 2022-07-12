package org.labkey.api.settings;

import org.apache.logging.log4j.Logger;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.ExceptionReportingLevel;
import org.labkey.api.util.SafeToRenderEnum;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.util.logging.LogHelper;

import java.net.URISyntaxException;
import java.util.Arrays;

// Site settings constants are defined here in the same order as on the site settings page
public enum SiteSettingsProperties implements StartupProperty, SafeToRenderEnum
{
    defaultDomain("Default email domain for authentication purposes. DO NOT USE... use Authentication.DefaultDomain instead.")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            AuthenticationManager.setDefaultDomain(UserManager.getGuestUser(), value);
            LOG.warn("Support for the \"SiteSettings.defaultDomain\" startup property will be removed shortly; use \"Authentication.DefaultDomain\" instead.");
        }
    },
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
    useContainerRelativeURL("Use \"path first\" urls (/home/project-begin.view)")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setUseContainerRelativeURL(Boolean.parseBoolean(value));
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
    maxBLOBSize("Maximum file size, in bytes, to allow in database BLOBs")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setMaxBLOBSize(Integer.parseInt(value));
        }
    },
    ext3Required("Require ExtJS v3.4.1 be loaded on each page (DEPRECATED)")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setExt3Required(Boolean.parseBoolean(value));
        }
    },
    ext3APIRequired("Require ExtJS v3.x based Client API be loaded on each page (DEPRECATED)")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setExt3APIRequired(Boolean.parseBoolean(value));
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
    XFrameOption("Controls whether or not a browser may render a server page in a <frame> , <iframe> or <object>. Valid values: [SAMEORIGIN, ALLOW]")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setXFrameOption(value);
        }
    },
    navAccessOpen("Always include inaccessible parent folders in project menu when child folder is accessible")
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setNavAccessOpen(Boolean.parseBoolean(value));
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
