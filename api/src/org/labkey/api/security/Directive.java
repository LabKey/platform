package org.labkey.api.security;

import org.labkey.api.util.SafeToRenderEnum;

/**
 * All CSP directives that support substitutions. These constant names are persisted to the database, so be careful with
 * any changes. If adding a Directive, make sure to add the corresponding substitutions to application.properties.
 */
public enum Directive implements SafeToRenderEnum
{
    Connection("connect-src"),
    Font("font-src"),
    Frame("frame-src"),
    Image("image-src"),
    Style("style-src");

    private final String _cspDirective;

    Directive(String cspDirective)
    {
        _cspDirective = cspDirective;
    }

    public String getCspDirective()
    {
        return _cspDirective;
    }

    public String getSubstitutionKey()
    {
        return name().toUpperCase() + ".SOURCES";
    }
}
