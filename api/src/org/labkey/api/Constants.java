/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
package org.labkey.api;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.VersionNumber;

import java.time.Year;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.Function;

/**
 * This class provides a single place to update system-wide constants used for sizing caches and other purposes.
 *
 * Created by adam on 10/22/2016.
 */
public class Constants
{
    /**
     * Returns the earliest core module schema version that this server will upgrade. This constant should be updated
     * every major release.
     */
    public static double getEarliestUpgradeVersion()
    {
        return 18.1;
    }

    /**
     * Returns the documentation folder name associated with this version of LabKey. These names have typically been the
     * version numbers of each major release, therefore, this constant should be updated just before every major release.
     */
    public static String getDocumentationVersion()
    {
        return "19.3";
    }

    /**
     * Returns the current "base" schema version, the lowest schema version for modules that LabKey manages. The year
     * portion gets incremented annually in late December, just before we create the xx.1 branch.
     */
    public static double getLowestSchemaVersion()
    {
        return 20.000;
    }

    /**
     * Returns the maximum number of modules supported by the system
     */
    public static int getMaxModules()
    {
        return 200;
    }

    /**
     * Returns the maximum number of containers supported by the system
     */
    public static int getMaxContainers()
    {
        return 100_000;
    }

    public static Collection<Double> getMajorSchemaVersions()
    {
        return SCHEMA_VERSIONS;
    }

    /**
     * Returns the next major schema version number, based on LabKey's standard schema numbering sequence, accommodating
     * the schema versioning change made in January 2020.
     */
    private static double getNextReleaseVersion()
    {
        return incrementVersion(getLowestSchemaVersion());
    }

    private static double incrementVersion(double version)
    {
        if (version == 19.30)
            return 20.000;

        return version < 19.30 ? changeVersion(version, fractional -> (3 == fractional ? 8 : 1)) : version + 1;
    }

    private static double decrementVersion(double version)
    {
        if (version == 20.000)
            return 19.30;

        return version < 20.000 ? changeVersion(version, fractional -> -(1 == fractional ? 8 : 1)) : version - 1;
    }

    private static double changeVersion(double version, Function<Integer, Integer> function)
    {
        int round = (int) Math.round(version * 10.0); // 163 or 171 or 182
        int fractional = round % 10;  // [1, 2, 3]

        return (round + function.apply(fractional)) / 10.0;
    }

    private static final Collection<Double> SCHEMA_VERSIONS;

    static
    {
        Collection<Double> list = new LinkedList<>();
        double version = getEarliestUpgradeVersion();

        while (version <= getNextReleaseVersion())
        {
            list.add(version);
            version = incrementVersion(version);
        }

        SCHEMA_VERSIONS = Collections.unmodifiableCollection(list);
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testVersions()
        {
            double version = 16.20;

            for (double expected : new double[]{16.30, 17.10, 17.20, 17.30, 18.10, 18.20, 18.30, 19.10, 19.20, 19.30, 20.000, 21.000, 22.000, 23.000})
            {
                double previous = version;
                version = incrementVersion(version);
                assertEquals(expected, version, 0);
                assertEquals(previous, decrementVersion(version), 0);
            }
        }

        @Test
        public void testLowestSchemaVersion()
        {
            double lowest = getLowestSchemaVersion();
            double expected = Math.round(Year.now().getValue() / 100.0);
            assertEquals("It's time to update Constants.getLowestSchemaVersion()!", expected, lowest, 0.0);
        }

        @Test
        public void testDocumentationLink()
        {
            String releaseVersion = AppProps.getInstance().getReleaseVersion();

            if (!AppProps.UNKNOWN_VERSION.equals(releaseVersion))
            {
                // If this is a production release version (e.g., 20.7, 21.3, 22.11) then the doc link should match
                VersionNumber vn = new VersionNumber(releaseVersion);
                if (Set.of(3, 7, 11).contains(vn.getMinor()))
                {
                    String currentVersion = vn.getMajor() + "." + vn.getMinor();
                    assertEquals("It's time to update Constants.getDocumentationVersion()!", currentVersion, getDocumentationVersion());
                }
            }
        }
    }
}
