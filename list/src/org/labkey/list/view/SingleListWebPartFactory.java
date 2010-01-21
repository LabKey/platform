/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.view.*;

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
        super("Single List", null, true, true);
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        Map<String, String> props = webPart.getPropertyMap();

        String listIdParam = props.get("listId");
        String viewName = props.get("viewName");
        String title = (null == props.get("title") ? "Single List" : props.get("title"));

        if (null == listIdParam)
            return new HtmlView(title, "There is no list selected to be displayed in this webpart");

        try
        {
            ListQueryForm form = new ListQueryForm(Integer.parseInt(listIdParam), portalCtx);

            if (null == form.getList())
                return new HtmlView(title, "List does not exist");

            form.setViewName(viewName);
            return new SingleListWebPart(form, props);
        }
        catch (NumberFormatException e)
        {
            return new HtmlView(title, "List id is invalid");
        }
    }

    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new JspView<Portal.WebPart>("/org/labkey/list/view/customizeSingleListWebPart.jsp", webPart);
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
            settings.setAllowChooseQuery(false);
            settings.setAllowChooseView(false);
        }
    }
}
