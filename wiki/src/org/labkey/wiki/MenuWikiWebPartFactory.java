/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
package org.labkey.wiki;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.wiki.model.WikiWebPart;

import java.util.HashMap;
import java.util.Map;

/*
* User: Mark Igra
* Date: Jan 25, 2009
*/
public class MenuWikiWebPartFactory extends WikiWebPartFactory
{
    public MenuWikiWebPartFactory()
    {
        super(WikiModule.WEB_PART_NAME + " Menu", "menubar");
    }

    @Override
    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        Map<String, String> props = new HashMap<>(webPart.getPropertyMap());
        if (null == props.get("webPartContainer"))
            props.put("webPartContainer", portalCtx.getContainer().getProject().getId());
        WikiWebPart v = new WikiWebPart(webPart.getRowId(), props);
        v.setEmbedded(true);
        return v;
    }
}
