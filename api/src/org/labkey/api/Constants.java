/*
 * Copyright (c) 2016 LabKey Corporation
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
        return 17.10;
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
}
