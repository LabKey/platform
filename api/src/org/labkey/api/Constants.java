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
        return 16.30;
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
