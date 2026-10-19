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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.ExceptionReportingLevel;
import org.labkey.api.util.SafeToRenderEnum;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.util.logging.LogHelper;

import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.function.Function;

// Site settings constants are defined here in the same order as on the site settings page
public enum SiteSettingsProperties implements StartupProperty, SafeToRenderEnum
{
    administratorContactEmail("Primary site administrator", String.class, p -> p.getAdministratorContactEmail(false))
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setAdministratorContactEmail(value);
        }
    },
    baseServerURL("Base server URL (used to create links in emails sent by the system)", String.class, AppProps::getBaseServerUrl)
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
    usageReportingLevel("Check for updates and report usage statistics to the LabKey team. Valid values: " + Arrays.toString(UsageReportingLevel.values()), UsageReportingLevel.class, AppProps::getUsageReportingLevel)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setUsageReportingLevel(UsageReportingLevel.valueOf(value));
        }
    },
    exceptionReportingLevel("Report exceptions to the LabKey team. Valid values: " + Arrays.toString(ExceptionReportingLevel.values()), ExceptionReportingLevel.class, AppProps::getExceptionReportingLevel)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setExceptionReportingLevel(ExceptionReportingLevel.valueOf(value));
        }
    },
    selfReportExceptions("Report exceptions to the local server", Boolean.class, AppProps::isSelfReportExceptions)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setSelfReportExceptions(Boolean.parseBoolean(value));
        }
    },
    memoryUsageDumpInterval("Log memory usage frequency in minutes, for debugging. Set to 0 to disable.", Integer.class, AppProps::getMemoryUsageDumpInterval)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setMemoryUsageDumpInterval(Integer.parseInt(value));
        }
    },
    readOnlyHttpRequestTimeout("Timeout in seconds for read-only HTTP requests, after which resources like DB connections and spawned processes will be killed. Set to 0 to disable.", Integer.class, AppProps::getReadOnlyHttpRequestTimeout)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setReadOnlyHttpRequestTimeout(Integer.parseInt(value));
        }
    },
    scriptExecutionTimeout("Timeout in seconds for server-side JavaScript such as trigger scripts. Measured in wall-clock time, including database and other Java operations invoked by the script. Set to 0 to disable.", Integer.class, AppProps::getScriptExecutionTimeout)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setScriptExecutionTimeout(Integer.parseInt(value));
        }
    },
    maxBLOBSize("Maximum file size, in bytes, to allow in database BLOBs", Integer.class, AppProps::getMaxBLOBSize)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setMaxBLOBSize(Integer.parseInt(value));
        }
    },
    sslRequired("Require SSL connections (users must connect via SSL)", Boolean.class, AppProps::isSSLRequired)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setSSLRequired(Boolean.parseBoolean(value));
        }
    },
    sslPort("SSL port number (specified in server config file)", Integer.class, AppProps::getSSLPort)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setSSLPort(Integer.parseInt(value));
        }
    },
    allowApiKeys("Let users create API keys", Boolean.class, AppProps::isAllowApiKeys)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setAllowApiKeys(Boolean.parseBoolean(value));
        }
    },
    apiKeyExpirationSeconds("API key expiration in seconds. -1 represents no expiration.", Integer.class, AppProps::getApiKeyExpirationSeconds)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setApiKeyExpirationSeconds(Integer.parseInt(value));
        }
    },
    allowSessionKeys("Let users create session keys, which are associated with the user's currents server session", Boolean.class, AppProps::isAllowSessionKeys)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setAllowSessionKeys(Boolean.parseBoolean(value));
        }
    },
    pipelineToolsDirectory("Semicolon-separated list of directories on the web server containing executables that are run for pipeline jobs (e.g. TPP or XTandem)", String.class, AppProps::getPipelineToolsDirectory)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setPipelineToolsDir(value);
        }
    },
    showRibbonMessage("Display ribbon bar message", Boolean.class, AppProps::isShowRibbonMessage)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setShowRibbonMessage(Boolean.parseBoolean(value));
        }
    },
    ribbonMessage("Ribbon bar message HTML", String.class, AppProps::getRibbonMessage)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setRibbonMessage(value);
        }
    },
    adminOnlyMode("Admin only mode (only site admins may log in)", Boolean.class, AppProps::isUserRequestedAdminOnlyMode)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setUserRequestedAdminOnlyMode(Boolean.parseBoolean(value));
        }
    },
    adminOnlyMessage("Message to users when site is in admin-only mode (Wiki formatting allowed)", String.class, AppProps::getAdminOnlyMessage)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setAdminOnlyMessage(value);
        }
    },
    navAccessOpen("Always include inaccessible parent folders in project menu when child folder is accessible", Boolean.class, AppProps::isNavigationAccessOpen)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setNavAccessOpen(Boolean.parseBoolean(value));
        }
    },
    includeServerHttpHeader("If set to false, do not include a 'Server' header in HTTP responses", Boolean.class, AppProps::isIncludeServerHttpHeader)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setIncludeServerHttpHeader(Boolean.parseBoolean(value));
        }
    },
    termsOfUseFrequencySeconds("Require terms-of-use acceptance frequency in seconds. 0 = every sign-in, or positive number of seconds between required re-acceptance.", Integer.class, AppProps::getTermsOfUseFrequencySeconds)
    {
        @Override
        public void setValue(WriteableAppProps writeable, String value)
        {
            writeable.setTermsOfUseFrequencySeconds(Integer.parseInt(value));
        }
    };

    private final static Logger LOG = LogHelper.getLogger(SiteSettingsProperties.class, "Warnings about setting properties");

    private final String _description;
    private final Class<?> _valueClass;
    private final Function<AppProps, Object> _getter;

    SiteSettingsProperties(String description, Class<?> valueClass, Function<AppProps, Object> getter)
    {
        _description = description;
        _valueClass = valueClass;
        _getter = getter;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    /** Boolean, Integer, String, or an enum class */
    public Class<?> getValueClass()
    {
        return _valueClass;
    }

    /** Empty strings read as null, since saving null stores "" */
    public @Nullable Object getValue(AppProps props)
    {
        Object value = _getter.apply(props);
        return "".equals(value) ? null : value;
    }

    /**
     * Converts a JSON value to this setting's value class. Unlike {@link #setValue}, rejects anything that isn't an
     * exact match, e.g., "yes" for a boolean.
     * @throws IllegalArgumentException if the value doesn't convert
     */
    public @Nullable Object parseJson(@Nullable Object json)
    {
        if (_valueClass == String.class)
        {
            if (json == null || json instanceof String)
                return "".equals(json) ? null : json;
        }
        else if (_valueClass == Boolean.class)
        {
            if (json instanceof Boolean)
                return json;
            if (json instanceof String s && (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false")))
                return Boolean.valueOf(s);
        }
        else if (_valueClass == Integer.class)
        {
            try
            {
                if (json instanceof Number n)
                    return new BigDecimal(n.toString()).intValueExact();
                if (json instanceof String s)
                    return Integer.parseInt(s.trim());
            }
            catch (ArithmeticException | NumberFormatException ignored)
            {
            }
        }
        else if (json instanceof String s)
        {
            for (Object constant : _valueClass.getEnumConstants())
            {
                if (((Enum<?>) constant).name().equals(s))
                    return constant;
            }
        }

        throw new IllegalArgumentException("Invalid value for " + name() + ": " + json + ". Expected " + describeValueClass() + ".");
    }

    private String describeValueClass()
    {
        if (_valueClass == Boolean.class)
            return "true or false";
        if (_valueClass == Integer.class)
            return "an integer";
        if (_valueClass == String.class)
            return "a string";
        return "one of " + Arrays.toString(_valueClass.getEnumConstants());
    }

    /** Applies a value returned by {@link #parseJson} */
    public void applyValue(WriteableAppProps writeable, @Nullable Object value)
    {
        setValue(writeable, value instanceof Enum<?> e ? e.name() : value == null ? null : value.toString());
    }

    public abstract void setValue(WriteableAppProps writeable, String value);
}
