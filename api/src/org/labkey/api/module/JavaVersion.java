/*
 * Copyright (c) 2019 LabKey Corporation
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

import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.annotations.JavaRuntimeVersion;
import org.labkey.api.util.ConfigurationException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

@JavaRuntimeVersion
public enum JavaVersion
{
    JAVA_UNSUPPORTED(-1, true, false, null),
    JAVA_17(17, false, true, "https://docs.oracle.com/en/java/javase/17/docs/api/java.base/"),
    JAVA_FUTURE(Integer.MAX_VALUE, false, false, "https://docs.oracle.com/en/java/javase/17/docs/api/java.base/");

    private final int _version;
    private final boolean _deprecated;
    private final boolean _tested;
    private final String _javaDocBaseURL;

    JavaVersion(int version, boolean deprecated, boolean tested, String javaDocBaseURL)
    {
        _version = version;
        _deprecated = deprecated;
        _tested = tested;
        _javaDocBaseURL = javaDocBaseURL;
    }

    public boolean isDeprecated()
    {
        return _deprecated;
    }

    public boolean isTested()
    {
        return _tested;
    }

    public String getJavaDocBaseURL()
    {
        return _javaDocBaseURL;
    }

    private final static Map<Integer, JavaVersion> VERSION_MAP = Arrays.stream(values())
        .collect(Collectors.toMap(jv->jv._version, jv->jv));

    private final static int MAX_KNOWN_VERSION = Arrays.stream(values())
        .filter(v->JAVA_FUTURE != v)
        .map(v->v._version)
        .max(Comparator.naturalOrder())
        .orElseThrow();

    public static JavaVersion get()
    {
        // Determine current Java specification version, normalized to an int (e.g., 10, 11, 12, 13, 14, 15, 16, 17...).
        // Commons lang methods like SystemUtils.isJavaVersionAtLeast() aren't an option because that library isn't
        // released often enough to keep up with the Java rapid release cadence.
        String[] versionArray = SystemUtils.JAVA_SPECIFICATION_VERSION.split("\\.");

        int version = Integer.parseInt("1".equals(versionArray[0]) ? versionArray[1] : versionArray[0]);

        JavaVersion jv = get(version);

        if (JAVA_UNSUPPORTED == jv)
            throw new ConfigurationException("Unsupported Java runtime version: " + getJavaVersionDescription() + ". We recommend installing " + getRecommendedJavaVersion() + ".");

        return jv;
    }

    private static @NotNull JavaVersion get(int version)
    {
        if (version > MAX_KNOWN_VERSION)
        {
            return JAVA_FUTURE;
        }
        else
        {
            JavaVersion jv = VERSION_MAP.get(version);
            return null != jv ? jv : JAVA_UNSUPPORTED;
        }
    }

    // Version information to display in administrator warnings
    public static String getJavaVersionDescription()
    {
        return SystemUtils.JAVA_VERSION;
    }

    public static String getRecommendedJavaVersion()
    {
        return "Eclipse Temurin 17 64-bit with HotSpot JVM";
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            // Bad
            test(5, JAVA_UNSUPPORTED);
            test(6, JAVA_UNSUPPORTED);
            test(7, JAVA_UNSUPPORTED);
            test(8, JAVA_UNSUPPORTED);
            test(9, JAVA_UNSUPPORTED);
            test(10, JAVA_UNSUPPORTED);
            test(11, JAVA_UNSUPPORTED);
            test(12, JAVA_UNSUPPORTED);
            test(13, JAVA_UNSUPPORTED);
            test(14, JAVA_UNSUPPORTED);
            test(15, JAVA_UNSUPPORTED);
            test(16, JAVA_UNSUPPORTED);

            // Good
            test(17, JAVA_17);

            // Future
            test(18, JAVA_FUTURE);
            test(19, JAVA_FUTURE);
        }

        private void test(int version, JavaVersion expectedVersion)
        {
            Assert.assertEquals(get(version), expectedVersion);
        }
    }
}
