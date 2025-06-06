/*
 * Copyright (c) 2015-2019 LabKey Corporation
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
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.list.ListUrls;
import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.writer.HtmlWriter;
import org.labkey.list.controllers.ListController;

import java.io.PrintWriter;
import java.util.Map;
import java.util.TreeSet;

import static org.labkey.api.view.WebPartFactory.LOCATION_BODY;
import static org.labkey.api.view.WebPartFactory.LOCATION_RIGHT;

public class ListsWebPart extends WebPartView<ViewContext>
{
    public static final BaseWebPartFactory FACTORY = new AlwaysAvailableWebPartFactory("Lists", LOCATION_BODY, LOCATION_RIGHT)
    {
        @Override
        public ListsWebPart getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
        {
            boolean narrow = webPart.getLocation().equals(LOCATION_RIGHT);
            return new ListsWebPart(narrow, portalCtx);
        }
    };

    private final boolean _narrow;

    public ListsWebPart(boolean narrow, ViewContext portalCtx)
    {
        super(new ViewContext(portalCtx));
        _narrow = narrow;

        setTitle("Lists");
        setTitleHref(ListController.getBeginURL(portalCtx.getContainer()));

        if (portalCtx.hasPermission(DesignListPermission.class))
        {
            NavTree menu = new NavTree("");
            menu.addChild("Create New List", PageFlowUtil.urlProvider(ListUrls.class).getCreateListURL(portalCtx.getContainer()));
            menu.addChild("Manage Lists", PageFlowUtil.urlProvider(ListUrls.class).getManageListsURL(portalCtx.getContainer()));
            setNavMenu(menu);
        }
    }

    @Override
    protected void renderView(ViewContext model, PrintWriter oldWriter, HtmlWriter out) throws Exception
    {
        if (_narrow)
            renderNarrowView(model, oldWriter);
        else
            include(new JspView<Object>("/org/labkey/list/view/listsWebPart.jsp", model));
    }

    private void renderNarrowView(ViewContext model, PrintWriter oldWriter)
    {
        Map<String, ListDefinition> lists = ListService.get().getLists(model.getContainer(), model.getUser(), true, true, false);
        oldWriter.write("<table>");
        if (lists.isEmpty())
        {
            oldWriter.write("<tr><td>There are no user-defined lists in this folder.</td></tr>");
        }
        else
        {
            for (ListDefinition list : new TreeSet<>(lists.values()))
            {
                oldWriter.write("<tr><td><a href=\"");
                oldWriter.write(PageFlowUtil.filter(list.urlShowData()));
                oldWriter.write("\">");
                oldWriter.write(PageFlowUtil.filter(list.getName()));
                oldWriter.write("</a></td></tr>");
            }
        }
        oldWriter.write("</table>");
        if (model.getContainer().hasPermission(model.getUser(), DesignListPermission.class))
            oldWriter.write(LinkBuilder.labkeyLink("manage lists", PageFlowUtil.urlProvider(ListUrls.class).getManageListsURL(model.getContainer())).toString());
    }
}
