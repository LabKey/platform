package org.labkey.api.security;

import org.labkey.api.settings.StartupProperty;
import org.labkey.api.util.SafeToRenderEnum;

/**
 * All CSP directives that support substitutions. These constant names are persisted to the database, so be careful with
 * any changes. If adding a Directive, make sure to add the corresponding substitutions in LabKeyServer baseCsp.
 */
public enum Directive implements StartupProperty, SafeToRenderEnum
{
    Connection("connect-src", "Sources for fetch/XHR requests"),
    Font("font-src", "Sources for fonts"),
    Frame("frame-src", "Sources for iframes"),
    Image("image-src", "Sources for images"),
    Object("object-src", "Sources for objects"), // Issue 53226
    Style("style-src", "Sources for stylesheets");

    private final String _cspDirective;
    private final String _description;

    Directive(String cspDirective, String description)
    {
        _cspDirective = cspDirective;
        _description = description;
    }

    public String getCspDirective()
    {
        return _cspDirective;
    }

    public String getSubstitutionKey()
    {
        return name().toUpperCase() + ".SOURCES";
    }

    @Override
    public String getDescription()
    {
        return _description + " (" + _cspDirective + "). Multiple hosts, separated by a space, can be provided.";
    }
}
