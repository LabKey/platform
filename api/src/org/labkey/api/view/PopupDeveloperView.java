/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
package org.labkey.api.view;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.query.QueryUrls;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: dave
 * Date: Sep 10, 2009
 * Time: 3:13:32 PM
 */
public class PopupDeveloperView  extends PopupMenuView
{
    public PopupDeveloperView(ViewContext context)
    {
        NavTree navTree = new NavTree("Developer");

        if (context.getUser().isDeveloper())
            navTree.addChildren(getNavTree(context));

        navTree.setId("devMenu");
        setNavTree(navTree);
        setAlign(PopupMenu.Align.RIGHT);
        setButtonStyle(PopupMenu.ButtonStyle.BOLDTEXT);
    }

    public static List<NavTree> getNavTree(ViewContext context)
    {
        Container container = context.getContainer();
        ArrayList<NavTree> items = new ArrayList<NavTree>();
        if (!container.isRoot())
            items.add(new NavTree("Schema Browser", PageFlowUtil.urlProvider(QueryUrls.class).urlSchemaBrowser(container)));
        items.add(new NavTree("JavaScript API Reference", "https://www.labkey.org/download/clientapi_docs/javascript-api/"));
        items.add(new NavTree("XML Schema Reference", "https://www.labkey.org/download/schema-docs/xml-schemas"));
        return items;
    }
}
