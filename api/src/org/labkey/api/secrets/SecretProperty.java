package org.labkey.api.secrets;

import org.labkey.api.settings.StartupProperty;

/**
 * Describes a named secret that a module needs to access. Register instances with
 * {@link SecretService#register} during module startup; retrieve values via
 * {@link SecretService#getSecret}.
 *
 * Startup property file convention: {@code secret.<propertyName>=<value>}
 * Environment variable convention:  {@code labkey.prop.secret.<propertyName>=<value>}
 */
public class SecretProperty implements StartupProperty
{
    private final String _name;
    private final String _description;

    public SecretProperty(String name)
    {
        this(name, "Secret: " + name);
    }

    public SecretProperty(String name, String description)
    {
        _name = name;
        _description = description;
    }

    @Override
    public String getPropertyName()
    {
        return _name;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }
}
