/*
 * Copyright (c) 2016-2017 LabKey Corporation
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

/**
 * This class provides a single place to update system-wide constants used for sizing caches and other purposes.
 *
 * Created by adam on 10/22/2016.
 */
public class Constants
{
    /**
     * The most recent official release version number is used to generate help topics, tag all code-only modules, and
     * drive the script consolidation process. This constant should be updated just before branching each major release.
     *
     * @return The last official release version number
     */
    public static double getPreviousReleaseVersion()
    {
        return 18.10;
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

    private static double incrementVersion(double previous)
    {
        int round = (int) Math.round(previous * 10.0); // 163 or 171 or 182
        int fractional = round % 10;  // [1, 2, 3]

        return (round + (3 == fractional ? 8 : 1)) / 10.0;
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

            for (double expected : new double[]{16.30, 17.10, 17.20, 17.30, 18.10, 18.20})
            {
                version = incrementVersion(version);
                assertEquals(expected, version, 0);
            }
        }
    }
}
