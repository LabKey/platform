/*
 * Copyright (c) 2017-2018 LabKey Corporation
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
package org.labkey.api.module;

import org.labkey.api.util.ConfigurationException;

/**
 * Enum that specifies the versions of Apache Tomcat that LabKey supports plus their properties
 *
 * Created by adam on 5/27/2017.
 */
public enum TomcatVersion
{
    TOMCAT_7_0(false, "getMaxActive", "Connector", "keystoreFile", "keystorePass", "keystoreType"),
    TOMCAT_8_5(false, "getMaxTotal", "SSLHostConfigCertificate", "certificateKeystoreFile", "certificateKeystorePassword", "certificateKeystoreType"),
    TOMCAT_9_0(false, "getMaxTotal", "SSLHostConfigCertificate", "certificateKeystoreFile", "certificateKeystorePassword" , "certificateKeystoreType");

    private final boolean _deprecated;
    private final String _maxTotalMethodName;
    private final String _sslConfigPropName;
    private final String _keystoreFilePropertyName;
    private final String _keystorePasswordPropertyName;
    private final String _keystoreTypePropertyName;

    TomcatVersion(boolean deprecated, String maxTotalMethodName, String sslConfigPropName, String keystoreFilePropertyName, String keystorePasswordPropertyName, String keystoreTypePropertyName)
    {
        _deprecated = deprecated;
        _maxTotalMethodName = maxTotalMethodName;
        _sslConfigPropName = sslConfigPropName;
        _keystoreFilePropertyName = keystoreFilePropertyName;
        _keystorePasswordPropertyName = keystorePasswordPropertyName;
        _keystoreTypePropertyName = keystoreTypePropertyName;
    }

    // Should LabKey warn administrators that support for this Tomcat version will be removed soon?
    public boolean isDeprecated()
    {
        return _deprecated;
    }

    public String getMaxTotalMethodName()
    {
        return _maxTotalMethodName;
    }

    public String getSslConfigPropName()
    {
        return _sslConfigPropName;
    }

    public String getKeystoreFilePropertyName()
    {
        return _keystoreFilePropertyName;
    }

    public String getKeystorePasswordPropertyName()
    {
        return _keystorePasswordPropertyName;
    }

    public String getKeystoreTypePropertyName()
    {
        return _keystoreTypePropertyName;
    }

    private static final String APACHE_TOMCAT_SERVER_NAME_PREFIX = "Apache Tomcat/";

    public static TomcatVersion get()
    {
        String serverInfo = ModuleLoader.getServletContext().getServerInfo();

        if (serverInfo.startsWith(APACHE_TOMCAT_SERVER_NAME_PREFIX))
        {
            String[] versionParts = serverInfo.substring(APACHE_TOMCAT_SERVER_NAME_PREFIX.length()).split("\\.");

            if (versionParts.length > 1)
            {
                int majorVersion = Integer.valueOf(versionParts[0]);
                int minorVersion = Integer.valueOf(versionParts[1]);

                switch (majorVersion * 10 + minorVersion)
                {
                    case 70:
                        return TomcatVersion.TOMCAT_7_0;
                    case 85:
                        return TomcatVersion.TOMCAT_8_5;
                    case 90:
                        return TomcatVersion.TOMCAT_9_0;
                }
            }
        }

        throw new ConfigurationException("Unsupported Tomcat version: " + serverInfo + ". LabKey Server requires Apache Tomcat 7.0.x, 8.5.x, or 9.0.x.");
    }
}
