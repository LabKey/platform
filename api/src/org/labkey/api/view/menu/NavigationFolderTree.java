/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.api.view.menu;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.labkey.api.util.PageFlowUtil.filter;

/**
 * User: Nick
 * Date: 4/10/13
 */

//TODO: Goal given a root folder, this outputs a nice html list of the subfolders.
public final class NavigationFolderTree extends NavTreeMenu
{
    public NavigationFolderTree(ViewContext context)
    {
        super(context, "folder-nav-menu", null, null, false, false, getNavTree(context));
        setFrame(FrameType.NONE);
    }

    @Nullable
    public static List<NavTree> getNavTree(ViewContext context)
    {
        if (context.getContainer() != null)
        {
            // getFolderListForUser returns a single NavTree, so this function will always return a list with a single element.
            return Collections.singletonList(ContainerManager.getFolderListForUser(ContainerManager.getRoot(), getRootContext()));
        }

        return null;
    }

    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        List<NavTree> elements = getElements();
        NavTree root;

        ViewContext context = getViewContext();
        List<Container> currentFolderLineage = ContainerManager.containersToRootList(context.getContainer());

        // as shown above in getNavTree, if elements is not null, then there will only be one element.
        if (null != elements && (root = elements.get(0)) != null && root.hasChildren())
        {
            renderChildLinks(root,"", root.getId(), context, currentFolderLineage, out);
        }
    }

    private void renderChildLinks(NavTree nav, String pathToHere, String rootId,
                                  ViewContext context, List<Container> lineage, PrintWriter out) throws URISyntaxException
    {
        out.print("<ul>");
        for (NavTree child: nav.getChildren())
        {
            renderLink(child, pathToHere, rootId, context, lineage, out);
        }
        out.print("</ul>");
    }

    private void renderLink(NavTree nav, String pathToHere, String rootId,
                            ViewContext context, List<Container> lineage, PrintWriter out) throws URISyntaxException
    {
        boolean expanded = !lineage.isEmpty() && null != nav.getHref() &&
                nav.getHref().equals(PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(lineage.get(0)).toString());
        if (expanded)
        {
            lineage.remove(0);
        }
        boolean hasChildren = nav.hasChildren();
        boolean selected = expanded && lineage.isEmpty();
        String link = nav.getHref();

        if (null != nav.getText())
            pathToHere = pathToHere + "/" + nav.getEscapedKey();

        out.print("<li " + (hasChildren ? "class=\"clbl" + (expanded ? " expand-folder" : " collapse-folder") + "\"" : "") + ">");

        out.print("<span");
        if (hasChildren)
            out.print(" class=\"marked\"");
        out.print(">&nbsp;</span>"); // Safari

        if (null != link)
        {
            if (!StringUtils.isEmpty(nav.getId()))
                out.printf("<a id=\"%s\" href=\"%s\"", filter(nav.getId()), filter(link));
            else
                out.printf("<a href=\"%s\"", filter(link));

            if (selected)
                out.print(" class=\"nav-tree-selected\" id=\"folder-target\"");

            if (nav.isNoFollow())
                out.print(" rel=\"nofollow\"");

            if (hasChildren)
            {
                ActionURL expandCollapseUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getExpandCollapseURL(getViewContext().getContainer(), pathToHere, rootId);
                out.printf(" expandurl=\"%s\"", filter(expandCollapseUrl));
            }

            out.print(">");
            out.print(filter(nav.getText()));
            out.print("</a>");
        }
        else
        {
            out.print("<span class=\"noread\">");
            out.print(filter(nav.getText()));
            out.print("</span>");
        }

        if (hasChildren)
        {
            renderChildLinks(nav, pathToHere, rootId, context, lineage, out);
        }
        out.print("</li>");
    }
}
