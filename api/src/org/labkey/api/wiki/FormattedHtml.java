/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
import org.labkey.api.view.template.ClientDependency;

import java.util.HashSet;
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
    private final String _html;
    private final Set<String> _wikiDependencies;
    private Set<ClientDependency> _clientDependencies = new LinkedHashSet<>();

    public FormattedHtml(String html)
    {
        this(html, false);
    }

    public FormattedHtml(String html, boolean isVolatile)
    {
        this(html, isVolatile, new HashSet<String>());
    }

    public FormattedHtml(String html, boolean isVolatile, Set<String> wikiDependencies)
    {
        this(html, isVolatile, wikiDependencies, null);
    }

    public FormattedHtml(String html, boolean isVolatile, Set<String> wikiDependencies, LinkedHashSet<ClientDependency> clientDependencies)
    {
        _html = html;
        _volatile = isVolatile;
        _wikiDependencies = wikiDependencies;
        if (clientDependencies != null)
            _clientDependencies.addAll(clientDependencies);
    }

    public String getHtml()
    {
        return _html;
    }

    public boolean isVolatile()
    {
        return _volatile;
    }

    public Set<String> getWikiDependencies()
    {
        return _wikiDependencies;
    }

    @NotNull
    public Set<ClientDependency> getClientDependencies()
    {
        return _clientDependencies;
    }
}
