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
    private final String _propertyName;
    private final String _title;
    private final String _description;
    private final boolean _requiresRestart;
    private final boolean _hidden;
    private final FeatureType _type;

    /**
     * @param flag must be unique and conform to the Java identifier rules (e.g., alphanumeric plus _, start with a
     *             letter, no spaces). That way it can be used as a startup property to enable/disable the task.
     */
    public OptionalFeatureFlag(String flag, String title, String description, boolean requiresRestart, boolean hidden, FeatureType type)
    {
        this(flag, title, description, requiresRestart, hidden, type, false);
    }

    OptionalFeatureFlag(String flag, String title, String description, boolean requiresRestart, boolean hidden, FeatureType type, boolean useDumbName /* true means allow a name that can't be used as a startup property name */)
    {
        _flag = flag;
        _title = title;
        _description = description;
        _requiresRestart = requiresRestart;
        _hidden = hidden;
        _type = type;
        if (!StringUtilsLabKey.isValidJavaIdentifier(_flag))
        {
            if (useDumbName)
            {
                _propertyName = null;
            }
            else
            {
                throw new IllegalStateException(_flag + " is not a valid Java identifier. Correct it so it can be used as a startup property. Or set useDumbName to true.");
            }
        }
        else
        {
            _propertyName = _flag;
        }
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
        return _propertyName;
    }
}
