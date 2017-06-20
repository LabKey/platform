package org.labkey.api.settings;

import org.jetbrains.annotations.Nullable;

/**
 * Created by klum on 6/12/2017.
 */
public class ConfigProperty
{
    private String _name;
    private String _value;
    private modifier _modifier = modifier.bootstrap;
    private String _scope = DEFAULT_SCOPE;

    public static final String DEFAULT_SCOPE = "none";
    public static final String SYS_PROP_PREFIX = "labkey.prop.";

    public enum modifier
    {
        bootstrap,
        startup,
        immutable,
    }

    public ConfigProperty(String name, String value, @Nullable String modifierType, @Nullable String scope)
    {
        _name = name;
        _value = value;
        if (modifierType != null)
            _modifier = modifier.valueOf(modifierType);
        if (scope != null)
            _scope = scope;
    }

    public String getValue()
    {
        return _value;
    }

    public String getName()
    {
        return _name;
    }

    public modifier getModifier()
    {
        return _modifier;
    }

    public String getScope()
    {
        return _scope;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConfigProperty config = (ConfigProperty) o;

        if (!_name.equals(config.getName())) return false;
        else if (!_scope.equals(config.getScope())) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = 31 * _name.hashCode();
        result = 31 * result + _scope.hashCode();

        return result;
    }
}
