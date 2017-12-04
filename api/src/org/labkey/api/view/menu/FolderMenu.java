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
import org.labkey.api.data.ContainerManager;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.labkey.api.util.PageFlowUtil.filter;

/**
 * User: Nick
 * Date: 4/10/13
 */
public class FolderMenu extends NavTreeMenu
{
    public FolderMenu(ViewContext context)
    {
        super(context, "folder-nav-menu", null, null, false, false, getNavTree(context));
        setFrame(FrameType.NONE);
    }

    @Nullable
    public static List<NavTree> getNavTree(ViewContext context)
    {
        if (context.getContainer() != null)
        {
            return Collections.singletonList(ContainerManager.getFolderListForUser(ContainerManager.getRoot(), context));
        }

        return null;
    }

    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        NavTree root;
        List<NavTree> elements = getElements();
        ViewContext context = getViewContext();

        // as shown above in getNavTree, if elements is not null, then there will only be one element.
        if (null != elements && (root = elements.get(0)) != null && root.hasChildren())
        {
            out.print("<div class=\"folder-nav\">");
            renderChildLinks(root, "", root.getId(), context, out, null);
            out.print("</div>");
        }
    }

    private void renderChildLinks(NavTree nav, String pathToHere, String rootId,
                                  ViewContext context, PrintWriter out, @Nullable Boolean shouldExpand) throws URISyntaxException
    {
        out.print("<ul>");
        for (NavTree child: nav.getChildren())
        {
            renderLink(child, pathToHere, rootId, context, out, shouldExpand);
        }
        out.print("</ul>");
    }

    private void renderLink(NavTree nav, String pathToHere, String rootId,
                            ViewContext context, PrintWriter out, Boolean shouldExpand) throws URISyntaxException
    {
        final String currentPath = (pathToHere + "/" + nav.getText()).toLowerCase();
        final String containerPath = context.getContainer().getPath().toLowerCase();

        if (shouldExpand == null)
            shouldExpand = containerPath.startsWith(currentPath);

        boolean isSelected = shouldExpand && currentPath.equals(containerPath);
        boolean hasChildren = nav.hasChildren();

        if (null != nav.getText())
            pathToHere = pathToHere + "/" + nav.getEscapedKey();

        List<String> liCls = new ArrayList<>();
        liCls.add("folder-tree-node");
        if (hasChildren)
        {
            liCls.add("clbl");
            if (shouldExpand)
                liCls.add("expand-folder");
            else
                liCls.add("collapse-folder");
        }

        out.print("<li" + (!liCls.isEmpty() ? " class=\"" + String.join(" ", liCls) + "\"" : "") + ">");

        out.print("<span");
        if (hasChildren)
            out.print(" class=\"marked\"");
        out.print(">&nbsp;</span>"); // Safari

        String link = nav.getHref();
        if (null != link)
        {
            if (!StringUtils.isEmpty(nav.getId()))
                out.printf("<a id=\"%s\" href=\"%s\"", filter(nav.getId()), filter(link));
            else
                out.printf("<a href=\"%s\"", filter(link));

            if (isSelected)
                out.print(" class=\"nav-tree-selected\" id=\"folder-target\"");

            if (nav.isNoFollow())
                out.print(" rel=\"nofollow\"");

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
            if (shouldExpand)
                renderChildLinks(nav, pathToHere, rootId, context, out, null);
            else
                renderChildLinks(nav, pathToHere, rootId, context, out, false);
        }
        out.print("</li>");
    }
}
