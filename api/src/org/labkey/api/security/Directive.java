/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.security;

import org.labkey.api.settings.StartupProperty;
import org.labkey.api.util.SafeToRenderEnum;

/**
 * All CSP directives that support substitutions. These constant names are persisted to the database, so be careful
 * with any changes. If adding a Directive, make sure to add the corresponding substitutions to the appropriate CSP
 * template(s) in LabKeyServer.
 */
public enum Directive implements StartupProperty, SafeToRenderEnum
{
    Connection("connect-src", "Sources for fetch/XHR requests"),
    Font("font-src", "Sources for fonts"),
    Frame("frame-src", "Sources for iframes"),
    FrameAncestors("frame-ancestors", "Parent hosts allowed to embed this site's resources in an <iframe>, etc."),
    Image("image-src", "Sources for images"),
    Object("object-src", "Sources for objects"), // Issue 53226
    Script("script-src", "Sources for scripts"),
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
