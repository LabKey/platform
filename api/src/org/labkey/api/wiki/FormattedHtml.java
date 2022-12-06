/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.wiki;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.HtmlString;
import org.labkey.api.view.template.ClientDependency;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User: adam
 * Date: Aug 6, 2007
 * Time: 2:18:32 PM
 */
public class FormattedHtml
{
    // Indicates that rendered HTML can change even if passed in content remains static.  This can happen when
    // renderer uses external resources, for example, URL parameters pulled from ThreadLocal, AppProps, etc.
    // If the formatted HTML is volatile, we shouldn't cache the formatted contents.
    private final boolean _volatile;
    private final HtmlString _html;
    private final Set<String> _wikiDependencies;
    private final Set<String> _anchors;
    private final Set<ClientDependency> _clientDependencies;

    public FormattedHtml(HtmlString html)
    {
        this(html, false, Collections.emptySet(), Collections.emptySet(), new LinkedHashSet<>());
    }

    public FormattedHtml(HtmlString html, boolean isVolatile)
    {
        this(html, isVolatile, Collections.emptySet(), Collections.emptySet(), new LinkedHashSet<>());
    }

    public FormattedHtml(HtmlString html, boolean isVolatile, @NotNull Set<String> wikiDependencies, @NotNull Set<String> anchors)
    {
        this(html, isVolatile, wikiDependencies, anchors, new LinkedHashSet<>());
    }

    public FormattedHtml(HtmlString html, boolean isVolatile, @NotNull LinkedHashSet<ClientDependency> clientDependencies)
    {
        this(html, isVolatile, Collections.emptySet(), Collections.emptySet(), clientDependencies);
    }

    public FormattedHtml(HtmlString html, boolean isVolatile, @NotNull Set<String> wikiDependencies, @NotNull Set<String> anchors, @NotNull LinkedHashSet<ClientDependency> clientDependencies)
    {
        _html = null==html ? HtmlString.EMPTY_STRING : html;
        _volatile = isVolatile;
        _wikiDependencies = Collections.unmodifiableSet(wikiDependencies);
        _anchors = Collections.unmodifiableSet(anchors);
        _clientDependencies = Collections.unmodifiableSet(clientDependencies);
    }

    @NotNull
    public HtmlString getHtml()
    {
        return _html;
    }

    public boolean isVolatile()
    {
        return _volatile;
    }

    @NotNull
    public Set<String> getWikiDependencies()
    {
        return _wikiDependencies;
    }

    @NotNull
    public Set<String> getAnchors()
    {
        return _anchors;
    }

    @NotNull
    public Set<ClientDependency> getClientDependencies()
    {
        return _clientDependencies;
    }
}
