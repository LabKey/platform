/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.wiki.renderer;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.wiki.FormattedHtml;

import java.util.LinkedHashSet;
import java.util.Map;

public class ClientDependencySubstitutionHandler implements HtmlRenderer.SubstitutionHandler
{
    @NotNull
    public FormattedHtml getSubstitution(Map<String, String> params)
    {
        params = new CaseInsensitiveHashMap<>(params);
        if (!params.containsKey("path"))
        {
            return new FormattedHtml("<br><font class='error' color='red'>Error: must provide the path of the client dependencies</font>");
        }

        LinkedHashSet<ClientDependency> cds = new LinkedHashSet<>();
        ClientDependency cd = ClientDependency.fromPath(params.get("path"));
        if (cd == null)
            return new FormattedHtml("<br><font class='error' color='red'>Error: unknown path: " + params.get("path") + "</font>");

        cds.add(cd);

        return new FormattedHtml("", false, cds);
    }
}
