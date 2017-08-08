/*
 * Copyright (c) 2017 LabKey Corporation
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
import org.labkey.api.data.ContainerManager;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;

public class PopupFolderNavView extends PopupMenuView
{
    private ViewContext _context;

    public PopupFolderNavView(ViewContext context)
    {
        super();
        _context = context;
    }

    public void render(Writer out) throws IOException
    {
        renderFolderNavTree(ContainerManager.getProjectList(_context, true), out);
    }

    private void renderFolderNavTree(NavTree tree, Writer out) throws IOException
    {
        if (tree == null)
            return;

        // TODO: This should use PopupMenuView.renderTree
        Container container = _context.getContainer();
        ActionURL startURL = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(container);
        String pattern = startURL.getLocalURIString();

        for (NavTree child : tree.getChildren())
        {
            String text = PageFlowUtil.filter(child.getText());
            boolean selected = child.getHref() != null && matchPath(child.getHref(), _context.getActionURL(), pattern);
            String cls = selected ? "lk-project-nav-tree-selected" : null;

            if (child.hasChildren())
            {
                out.write("<li class=\"dropdown-submenu " + (cls != null ? cls : "") + "\">");
                renderLink(child, "subexpand-link " + (child.getHref() == null ? "lk-project-nav-disabled" : ""), out);
                out.write("<a class=\"subexpand subexpand-target\" tabindex=\"0\"><i class=\"fa fa-chevron-right\"></i></a>");
                out.write("<ul class=\"dropdown-layer-menu\">");
                out.write("<li><a class=\"subcollapse\" tabindex=\"0\"><i class=\"fa fa-chevron-left\"></i>" + text + "</a></li>");
                renderTreeDivider(out);
                renderFolderNavTree(child, out);
                out.write("</ul>");
                out.write("</li>");
            }
            else
            {
                renderTreeItem(child, cls, out);
            }
        }
    }

    public static void renderFolderNavTrailLink(Container container, User user, Writer out) throws IOException
    {
        String title = container.isProject() && container.equals(ContainerManager.getHomeContainer()) ? "Home" : container.getTitle();

        if (container.hasPermission(user, ReadPermission.class))
            out.write("<a href=\"" + PageFlowUtil.filter(container.getStartURL(user)) +"\">" + PageFlowUtil.filter(title) + "</a>");
        else
            out.write("<span class=\"lk-project-nav-disabled\">" + PageFlowUtil.filter(title) + "</span>");

        out.write("<i class=\"fa fa-chevron-right\"></i>");
    }

    private boolean matchPath(String link, ActionURL currentUrl, String pattern)
    {
        if (link.endsWith(pattern))
        {
            ActionURL urlLink;
            urlLink = new ActionURL(link);
            return currentUrl.getParsedPath().equals(urlLink.getParsedPath());
        }
        return false;
    }
}
