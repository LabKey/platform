package org.labkey.api.module;

/**
 * Created by adam on 5/27/2017.
 */
public enum TomcatVersion
{
    UNKNOWN,
    TOMCAT_7,
    TOMCAT_8,
    TOMCAT_9;

    public static TomcatVersion get(int majorVersion)
    {
        switch (majorVersion)
        {
            case (7): return TomcatVersion.TOMCAT_7;
            case (8): return TomcatVersion.TOMCAT_8;
            case (9): return TomcatVersion.TOMCAT_9;
            default: return TomcatVersion.UNKNOWN;
        }
    }
}
