package org.labkey.api.module;

import org.apache.commons.lang3.SystemUtils;
import org.labkey.api.annotations.JavaRuntimeVersion;
import org.labkey.api.util.ConfigurationException;

@JavaRuntimeVersion
public enum JavaVersion
{
    JAVA_12(false, true),
    JAVA_13(false, false),
    JAVA_FUTURE(false, false);

    private final boolean _deprecated;
    private final boolean _tested;

    JavaVersion(boolean deprecated, boolean tested)
    {
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

    public static JavaVersion get()
    {
        // Determine current Java specification version, normalized to an int (e.g., 8, 9, 10, 11, 12, 13...). Commons lang
        // methods like SystemUtils.isJavaVersionAtLeast() aren't an option because this library isn't released often enough
        // to keep up with the Java rapid release cadence.
        String[] versionArray = SystemUtils.JAVA_SPECIFICATION_VERSION.split("\\.");

        int version = Integer.parseInt("1".equals(versionArray[0]) ? versionArray[1] : versionArray[0]);

        switch (version)
        {
            case 12: return JAVA_12;
            case 13: return JAVA_13;
        }

        if (version < 12)
            throw new ConfigurationException("Unsupported Java runtime version: " + SystemUtils.JAVA_VERSION + ". LabKey Server requires Java 12. We recommend installing " + getRecommendedJavaVersion() + ".");

        return JAVA_FUTURE;
    }

    // Version information to display in administrator warnings
    public static String getJavaVersionDescription()
    {
        return SystemUtils.JAVA_VERSION;
    }

    public static String getRecommendedJavaVersion()
    {
        return "Oracle OpenJDK 12";
    }
}
