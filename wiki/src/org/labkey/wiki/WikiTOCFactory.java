/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;

import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: Nov 5, 2008
 * Time: 10:51:27 AM
 */
public class WikiTOCFactory extends BaseWebPartFactory
{
    public WikiTOCFactory()
    {
        super("Wiki Table of Contents", true, false, WebPartFactory.LOCATION_RIGHT);
        addLegacyNames("Wiki TOC");
    }

    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        WebPartView v = new WikiTOC(portalCtx, webPart);
        //TODO: Should just use setters
        populateProperties(v, webPart.getPropertyMap());
        return v;
    }

    @Override
    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new JspView<>("/org/labkey/wiki/view/customizeWikiToc.jsp", webPart);
    }

    @Override
    public Map<String, String> serializePropertyMap(ImportContext ctx, Map<String, String> propertyMap)
    {
        Map<String, String> serializedPropertyMap = new HashMap<>(propertyMap);

        // for webPartContainer property, use the container path instead of container id
        if (serializedPropertyMap.containsKey("webPartContainer"))
        {
            Container webPartContainer = ContainerManager.getForId(serializedPropertyMap.get("webPartContainer"));
            if (null != webPartContainer)
            {
                if(webPartContainer.equals(ctx.getContainer()))
                {
                    // Don't write this property if it is the default container for this webpart.
                    // Issue 22261: Incorrect links in the "Wiki Table of Contents" web part.
                    serializedPropertyMap.remove("webPartContainer");
                }
                else
                {
                    serializedPropertyMap.put("webPartContainer", webPartContainer.getPath());
                }
            }
        }

        return serializedPropertyMap;
    }

    @Override
    public Map<String, String> deserializePropertyMap(ImportContext ctx, Map<String, String> propertyMap)
    {
        Map<String, String> deserializedPropertyMap = new HashMap<>(propertyMap);

        // for the webPartContainer property, try to get the container ID from the specified path
        // if a container does not exist for the given path, use the container parameter (i.e. the current container)
        if (deserializedPropertyMap.containsKey("webPartContainer"))
        {
            Container webPartContainer = ContainerManager.getForPath(deserializedPropertyMap.get("webPartContainer"));
            if (null != webPartContainer)
            {
                deserializedPropertyMap.put("webPartContainer", webPartContainer.getId());
            }
            else
            {
                deserializedPropertyMap.put("webPartContainer", ctx.getContainer().getId());
            }
        }

        return deserializedPropertyMap;
    }

    @Override
    public boolean includeInExport(ImportContext ctx, Portal.WebPart webPart)
    {
        String containerId = webPart.getPropertyMap().get("webPartContainer");
        if (containerId != null)
        {
            // Return true if the "webPartContainer" property is the same as the container in the ImportContext.
            // Issue 22261: Incorrect links in the "Wiki Table of Contents" web part.
            Container webPartContainer = ContainerManager.getForId(containerId);
            return null == webPartContainer || webPartContainer.equals(ctx.getContainer());
        }
        return true;
    }
}
