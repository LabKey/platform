package org.labkey.api.security;

/**
 * All CSP directives that support substitutions. These constant names are persisted to the database, so be careful with
 * any changes. If adding a Directive, make sure to add the corresponding substitutions to application.properties.
 */
public enum Directive
{
    Connection("connect-src"),
    Font("font-src"),
    Frame("frame-src"),
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
        return "LABKEY.ALLOWED." + name().toUpperCase() + ".SOURCES";
    }
}
