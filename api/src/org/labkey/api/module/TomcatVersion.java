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

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.ConfigurationException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Enum that specifies the versions of Apache Tomcat that LabKey supports plus their properties
 *
 * Created by adam on 5/27/2017.
 */
public enum TomcatVersion
{
    TOMCAT_UNSUPPORTED(-1, true),
    TOMCAT_10_1(101, false),
    TOMCAT_FUTURE(Integer.MAX_VALUE, true);

    private final int _version;
    private final boolean _deprecated;

    TomcatVersion(int version, boolean deprecated)
    {
        _version = version;
        _deprecated = deprecated;
    }

    // Should LabKey warn administrators that support for this Tomcat version will be removed soon?
    public boolean isDeprecated()
    {
        return _deprecated;
    }

    private static final String APACHE_TOMCAT_SERVER_NAME_PREFIX = "Apache Tomcat/";

    private final static Map<Integer, TomcatVersion> VERSION_MAP = Arrays.stream(values())
        .collect(Collectors.toMap(jv->jv._version, jv->jv));

    private final static int MAX_KNOWN_VERSION = Arrays.stream(values())
        .filter(v->TOMCAT_FUTURE != v)
        .map(v->v._version)
        .max(Comparator.naturalOrder())
        .orElseThrow();

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

                TomcatVersion tv = get(majorVersion * 10 + minorVersion);

                if (TOMCAT_UNSUPPORTED != tv)
                    return tv;
            }
        }

        throw new ConfigurationException("Unsupported Tomcat version: " + serverInfo + ". LabKey Server requires Apache Tomcat 10.1.x.");
    }

    private static @NotNull TomcatVersion get(int version)
    {
        if (version > MAX_KNOWN_VERSION)
        {
            return TOMCAT_FUTURE;
        }
        else
        {
            TomcatVersion tv = VERSION_MAP.get(version);
            return null != tv ? tv : TOMCAT_UNSUPPORTED;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            // Good
            test(101, TOMCAT_10_1);

            // Future
            test(110, TOMCAT_FUTURE);
            test(120, TOMCAT_FUTURE);

            // Bad
            test(100, TOMCAT_UNSUPPORTED);
            test(90, TOMCAT_UNSUPPORTED);
            test(85, TOMCAT_UNSUPPORTED);
            test(80, TOMCAT_UNSUPPORTED);
            test(70, TOMCAT_UNSUPPORTED);
            test(60, TOMCAT_UNSUPPORTED);
            test(50, TOMCAT_UNSUPPORTED);
            test(40, TOMCAT_UNSUPPORTED);
        }

        private void test(int version, TomcatVersion expectedVersion)
        {
            Assert.assertEquals(get(version), expectedVersion);
        }
    }
}
