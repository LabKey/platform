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
    JAVA_UNSUPPORTED(-1, true, false),
    JAVA_12(12, true, true),
    JAVA_13(13, false, true),
    JAVA_14(14, false, true),
    JAVA_15(15, false, false),
    JAVA_FUTURE(Integer.MAX_VALUE, false, false);

    private final int _version;
    private final boolean _deprecated;
    private final boolean _tested;

    JavaVersion(int version, boolean deprecated, boolean tested)
    {
        _version = version;
        _deprecated = deprecated;
        _tested = tested;
    }

    public boolean isDeprecated()
    {
        return _deprecated;
    }

    public boolean isTested()
    {
        return _tested;
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
        // Determine current Java specification version, normalized to an int (e.g., 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16...). Commons lang
        // methods like SystemUtils.isJavaVersionAtLeast() aren't an option because that library isn't released often enough
        // to keep up with the Java rapid release cadence.
        String[] versionArray = SystemUtils.JAVA_SPECIFICATION_VERSION.split("\\.");

        int version = Integer.parseInt("1".equals(versionArray[0]) ? versionArray[1] : versionArray[0]);

        JavaVersion jv = get(version);

        if (JAVA_UNSUPPORTED == jv)
            throw new ConfigurationException("Unsupported Java runtime version: " + getJavaVersionDescription() + ". LabKey Server requires Java 13 or Java 14. We recommend installing " + getRecommendedJavaVersion() + ".");

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
        return "AdoptOpenJDK 14 with HotSpot JVM";
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            // Good
            test(12, JAVA_12);
            test(13, JAVA_13);
            test(14, JAVA_14);
            test(15, JAVA_15);

            // Future
            test(16, JAVA_FUTURE);
            test(17, JAVA_FUTURE);
            test(18, JAVA_FUTURE);
            test(19, JAVA_FUTURE);

            // Bad
            test(11, JAVA_UNSUPPORTED);
            test(10, JAVA_UNSUPPORTED);
            test(9, JAVA_UNSUPPORTED);
            test(8, JAVA_UNSUPPORTED);
            test(7, JAVA_UNSUPPORTED);
            test(6, JAVA_UNSUPPORTED);
            test(5, JAVA_UNSUPPORTED);
        }

        private void test(int version, JavaVersion expectedVersion)
        {
            Assert.assertEquals(get(version), expectedVersion);
        }
    }
}
