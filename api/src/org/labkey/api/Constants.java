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
import org.labkey.api.module.ModuleLoader;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.function.Function;

/**
 * This class provides a single place to update system-wide constants used for sizing caches and other purposes.
 *
 * Created by adam on 10/22/2016.
 */
public class Constants
{
    private static final Collection<Double> VALID_VERSIONS;

    static
    {
        Collection<Double> list = new LinkedList<>();
        double version = ModuleLoader.EARLIEST_UPGRADE_VERSION;

        while (version <= getNextReleaseVersion())
        {
            list.add(version);
            version = incrementVersion(version);
        }

        VALID_VERSIONS = Collections.unmodifiableCollection(list);
    }


    /**
     * The most recent official release version number is used to generate help topics, tag all code-only modules, and
     * drive the script consolidation process. This constant should be updated just before branching each major release.
     *
     * @return The last official release version number
     */
    public static double getPreviousReleaseVersion()
    {
        return 20.000;
    }

    /**
     * The next official release version number, based on LabKey's standard release numbering sequence, for example:
     * <p>
     * 17.10, 17.20, 17.30, 18.10, 18.20...
     * <p>
     * This is used in the script consolidation process.
     *
     * @return The next official release version number
     */
    public static double getNextReleaseVersion()
    {
        return incrementVersion(getPreviousReleaseVersion());
    }

    public static double incrementVersion(double version)
    {
        return changeVersion(version, fractional -> (3 == fractional ? 8 : 1));
    }

    public static double decrementVersion(double version)
    {
        return changeVersion(version, fractional -> -(1 == fractional ? 8 : 1));
    }

    private static double changeVersion(double version, Function<Integer, Integer> function)
    {
        int round = (int) Math.round(version * 10.0); // 163 or 171 or 182
        int fractional = round % 10;  // [1, 2, 3]

        return (round + function.apply(fractional)) / 10.0;
    }

    public static Collection<Double> getValidVersions()
    {
        return VALID_VERSIONS;
    }

    /**
     * @return The maximum number of modules supported by the system
     */
    public static int getMaxModules()
    {
        return 200;
    }

    /**
     * @return The maximum number of containers supported by the system
     */
    public static int getMaxContainers()
    {
        return 100_000;
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testVersions()
        {
            double version = 16.20;

            for (double expected : new double[]{16.30, 17.10, 17.20, 17.30, 18.10, 18.20, 18.30, 19.10, 19.20, 19.30, 20.10, 20.20, 20.30, 21.10})
            {
                double previous = version;
                version = incrementVersion(version);
                assertEquals(expected, version, 0);
                assertEquals(previous, decrementVersion(version), 0);
            }
        }
    }
}
