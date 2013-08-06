/*
 * Copyright (c) 2013 LabKey Corporation
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
import org.labkey.api.view.NavTreeManager;
import org.labkey.api.view.ViewContext;

import java.io.PrintWriter;
import java.net.URISyntaxException;

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
    private static NavTree getNavTree(ViewContext context)
    {
        if (context.getContainer().getProject() != null)
        {
            return ContainerManager.getFolderListForUser(context.getContainer().getProject(), context);
        }

        return null;
    }

    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        NavTree[] _elements = getElements();
        if (null != _elements)
        {
            out.print("<div class=\"folder-nav\"><ul class=\"folder-nav-top\">");
            for (NavTree element : _elements)
            {
                if (null != element)
                    renderLinks(element, 0, "", element.getId(), getViewContext(), out);
            }
            out.print("</ul></div>");
        }
    }

    private void renderLinks(NavTree nav, int level, String pathToHere, String rootId,
                             ViewContext context, PrintWriter out) throws URISyntaxException
    {
        Container c = context.getContainer();
        ActionURL currentUrl = context.getActionURL();
        if (c.isWorkbookOrTab())
        {
            currentUrl = currentUrl.clone();
            currentUrl.setPath(currentUrl.getParsedPath().getParent());
        }
        if (c.isWorkbookOrTab())
            c = c.getParent();
        ActionURL startURL = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c);
        String pattern = startURL.getLocalURIString();

        String link = nav.getHref() == null ? null : nav.getHref();
        boolean selected = _highlightSelection && null != link && matchPath(link, currentUrl, pattern);
        if (level == 0 && null != nav.getText())
            level = 1;

        boolean hasChildren = nav.hasChildren();

        // When we post the expanded path, we need to use the escaped key so that embedded
        // '/' characters in the key are not confused with path separators
        if (null != nav.getText())
            pathToHere = pathToHere + "/" + nav.getEscapedKey();

        boolean collapsed = nav.isCollapsed();

        if (hasChildren)
        {
            // the 'pathToHere' has been saved in the session if stateExpanded == true
            boolean stateExpanded = NavTreeManager.getExpandedPathsCopy(context, rootId).contains(pathToHere);

            if (collapsed && !stateExpanded)
            {
                Container folder = ContainerManager.getContainerService().getForPath(pathToHere);
                if (null != folder && (folder.isProject() || c.hasAncestor(folder)))
                {
                    collapsed = false;
                    if (folder.isProject())
                        NavTreeManager.expandCollapsePath(context, rootId, pathToHere, false);
                }
            }
            else if (stateExpanded)
            {
                collapsed = false; // respect the session setting
            }
        }

        if (level > 0)
        {
            out.print("<li " + (hasChildren ? "class=\"clbl" + (collapsed ? " collapse-folder" : " expand-folder") + "\"" : "") + ">");

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
                    ActionURL expandCollapseUrl = PageFlowUtil.urlProvider(ProjectUrls.class). getExpandCollapseURL(getViewContext().getContainer(), pathToHere, rootId);
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
        }

        if (hasChildren)
        {
            out.print("<ul>");
            for (NavTree child : nav.getChildren())
                renderLinks(child, level + 1, pathToHere, rootId, context, out);
            out.print("</ul>");
        }

        if (level > 0)
            out.print("</li>");
    }
}
