package org.labkey.api.settings;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.settings.OptionalFeatureService.FeatureType;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.logging.LogHelper;

public class OptionalFeatureFlag implements Comparable<OptionalFeatureFlag>, StartupProperty
{
    private static final Logger LOG = LogHelper.getLogger(OptionalFeatureFlag.class, "Warnings about optional feature flag names");

    private final String _flag;
    private final String _title;
    private final String _description;
    private final boolean _requiresRestart;
    private final boolean _hidden;
    private final FeatureType _type;

    /**
     * @param flag must be unique. Can be used as a startup property to enable/disable the task, but only if it follows
     *             the Java identifier rules (e.g., alphanumeric plus _, start with a letter, no spaces).
     */
    public OptionalFeatureFlag(String flag, String title, String description, boolean requiresRestart, boolean hidden, FeatureType type)
    {
        _flag = flag;
        _title = title;
        _description = description;
        _requiresRestart = requiresRestart;
        _hidden = hidden;
        _type = type;
    }

    public String getFlag()
    {
        return _flag;
    }

    public String getTitle()
    {
        return _title;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    public boolean isRequiresRestart()
    {
        return _requiresRestart;
    }

    @Override
    public int compareTo(@NotNull OptionalFeatureFlag o)
    {
        return getTitle().compareToIgnoreCase(o.getTitle());
    }

    public boolean isEnabled()
    {
        return AppProps.getInstance().isOptionalFeatureEnabled(getFlag());
    }

    public boolean isHidden()
    {
        return _hidden;
    }

    public FeatureType getType()
    {
        return _type;
    }

    // StartupProperty implementation

    /**
     * Returns {@code null} if {@code getFlag()} does not conform to the property name rules.
     */
    @Nullable
    @Override
    public String getPropertyName()
    {
        String name = getFlag();
        if (!StringUtilsLabKey.isValidJavaIdentifier(name))
        {
            // This is a developer thing... no need to log a warning on production deployments
            if (AppProps.getInstance().isDevMode())
                LOG.warn("Feature flag name doesn't conform to the property name rules so it won't be available as a startup property: {}", name);
            name = null;
        }
        return name;
    }
}
