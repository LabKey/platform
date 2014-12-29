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

package org.labkey.list.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.view.*;

import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: Nov 11, 2007
 * Time: 4:39:47 PM
 */
public class SingleListWebPartFactory extends AlwaysAvailableWebPartFactory
{
    public SingleListWebPartFactory()
    {
        super("List - Single", true, true);
        addLegacyNames("Single List");
    }

    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        Map<String, String> props = webPart.getPropertyMap();

        String listNameParam = props.get("listName");
        String listIdParam = props.get("listId");
        String viewName = props.get("viewName");
        String title = (null == props.get("title") ? "List" : props.get("title"));

        if (null == listNameParam && null == listIdParam)
            return new HtmlView(title, "There is no list selected to be displayed in this webpart");

        ListQueryForm form;

        if (listNameParam != null)
        {
            form = new ListQueryForm(listNameParam, portalCtx);
        }
        else
        {
            try
            {
                form = new ListQueryForm(Integer.parseInt(listIdParam), portalCtx);
            }
            catch (NumberFormatException e)
            {
                return new HtmlView(title, "List id is invalid");
            }
        }

        form.setDataRegionName("list" + webPart.getIndex());
        form.bindParameters(portalCtx.getBindPropertyValues());

        if (null == form.getList())
            return new HtmlView(title, "List does not exist");

        form.setViewName(viewName);
        return new SingleListWebPart(form, props);
    }

    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new JspView<>("/org/labkey/list/view/customizeSingleListWebPart.jsp", webPart);
    }

    private static class SingleListWebPart extends ListQueryView
    {
        private SingleListWebPart(ListQueryForm form, Map<String, String> props)
        {
            super(form, null);

            ListDefinition list = form.getList();
            String title = props.get("title");

            if (null == title)
                title = list.getName();

            setTitle(title);
            setTitleHref(list.urlShowData());

            QuerySettings settings = getSettings();
            settings.setAllowChooseView(false);
        }
    }

    @Override
    public Map<String, String> serializePropertyMap(ImportContext ctx, Map<String, String> propertyMap)
    {
        Map<String, String> serializedPropertyMap = new HashMap<>(propertyMap);

        // serialize the listName instead of listId, we'll try to resolve the listId on import based on the listName
        if (serializedPropertyMap.containsKey("listId"))
        {
            ListDefinition list = ListService.get().getList(ctx.getContainer(), Integer.parseInt(serializedPropertyMap.get("listId")));
            if (null != list)
            {
                serializedPropertyMap.put("listName", list.getName());
            }
            serializedPropertyMap.remove("listId");
        }

        return serializedPropertyMap;
    }

    @Override
    public Map<String, String> deserializePropertyMap(ImportContext ctx, Map<String, String> propertyMap)
    {
        Map<String, String> deserializedPropertyMap = new HashMap<>(propertyMap);

        // try to resolve the listId from the listName that was exported
        if (deserializedPropertyMap.containsKey("listName"))
        {
            ListDefinition list = ListService.get().getList(ctx.getContainer(), deserializedPropertyMap.get("listName"));
            if (null != list)
            {
                deserializedPropertyMap.put("listId", String.valueOf(list.getListId()));
            }
            deserializedPropertyMap.remove("listName");
        }

        return deserializedPropertyMap;
    }
}
