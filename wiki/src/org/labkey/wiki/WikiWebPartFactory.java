/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.wiki.model.WikiWebPart;

import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: Nov 5, 2008
 * Time: 10:50:37 AM
 */
public class WikiWebPartFactory extends AlwaysAvailableWebPartFactory
{
    protected WikiWebPartFactory(String name, @NotNull String defaultLocation, String... availableLocations)
    {
        super(name, true, false, defaultLocation, availableLocations);
    }

    public WikiWebPartFactory()
    {
        this(WikiModule.WEB_PART_NAME, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT);
        addLegacyNames("Narrow Wiki");
    }

    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        Map<String, String> props = webPart.getPropertyMap();
        return new WikiWebPart(webPart.getRowId(), props);
    }

    @Override
    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new WikiController.CustomizeWikiPartView(webPart);
    }

    @Override
    public Map<String, String> serializePropertyMap(ImportContext ctx, Map<String, String> propertyMap)
    {
        Map<String, String> serializedPropertyMap = new HashMap<>(propertyMap);

        // for the webPartContainer property, use the container path instead of container id
        // omit the container path in the common case where a wiki webpart is pointed at a wiki in its own container
        if (serializedPropertyMap.containsKey("webPartContainer"))
        {
            Container webPartContainer = ContainerManager.getForId(serializedPropertyMap.get("webPartContainer"));
            if (null != webPartContainer && !webPartContainer.equals(ctx.getContainer()))
            {
                serializedPropertyMap.put("webPartContainer", webPartContainer.getPath());
            }
            else
            {
                serializedPropertyMap.remove("webPartContainer");
            }
        }

        return serializedPropertyMap;
    }

    @Override
    public Map<String, String> deserializePropertyMap(ImportContext ctx, Map<String, String> propertyMap)
    {
        Map<String, String> deserializedPropertyMap = new HashMap<>(propertyMap);

        // for the webPartContainer property, try to get the container ID from the specified path
        // if a container does not exist for the given path, use the current container ID
        if (deserializedPropertyMap.size() > 0)
        {
            String containerId = ctx.getContainer().getId();
            if (deserializedPropertyMap.containsKey("webPartContainer"))
            {
                Container webPartContainer = ContainerManager.getForPath(deserializedPropertyMap.get("webPartContainer"));
                if (null != webPartContainer)
                {
                    containerId = webPartContainer.getId();
                }
            }
            deserializedPropertyMap.put("webPartContainer", containerId);
        }

        return deserializedPropertyMap;
    }
}
