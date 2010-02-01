/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

import org.labkey.api.data.Container;
import static org.labkey.api.util.PageFlowUtil.filter;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.portal.ProjectUrls;
import org.apache.commons.lang.StringUtils;

import java.io.PrintWriter;
import java.net.URISyntaxException;

/**
 * User: brittp
 * Date: Apr 9, 2007
 * Time: 10:06:16 AM
 */
public class NavTreeMenu extends WebPartView implements Collapsible
{
    private NavTree[] _elements;
    private String _collapseId;
    private boolean _highlightSelection = true;

    public NavTreeMenu(ViewContext context, String collapseId, String title, ActionURL titleHref, boolean collapseByDefault, NavTree... elements)
    {
        super(title);
        setFrame(FrameType.LEFT_NAVIGATION);
        _collapseId = collapseId;
        if (titleHref != null)
            setTitleHref(titleHref);
        _elements = elements;
        // assume we're collapsed:
        setCollapsed(collapseByDefault);
        NavTreeManager.applyExpandState(this, context);
    }

    public NavTreeMenu(ViewContext context,String rootId, String title, boolean collapseByDefault, NavTree... elements)
    {
        this(context, rootId, title, null, collapseByDefault, elements);
    }

    public NavTreeMenu(ViewContext context, String rootId, NavTree... elements)
    {
        this(context, rootId, null, null, false, elements);
    }

    public boolean isCollapsed()
    {
        Object collapsed = getViewContext().get("collapsed");
        return (collapsed instanceof Boolean) && ((Boolean) collapsed).booleanValue();
    }

    public void setCollapsed(boolean collapsed)
    {
        enableExpandCollapse(_collapseId, collapsed);
    }
    
    public void setHighlightSelection(boolean highlightSelection)
    {
        _highlightSelection = highlightSelection;
    }

    protected void setId(String id)
    {
        _collapseId = id;
    }
    
    protected void setElements(ViewContext context, NavTree... elements)
    {
        _elements = elements;
        NavTreeManager.applyExpandState(this, context);
    }

    public NavTree[] getChildren()
    {
        return null;
    }

    public NavTree[] getElements()
    {
        return _elements;
    }

    public Collapsible findSubtree(String path)
    {
        if (path == null)
            return this;
        else
            return null;
    }

    public String getId()
    {
        return _collapseId;
    }

    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        if (_elements != null)
        {
            boolean indentForExpansionGifs = false;
            for (NavTree element : _elements)
            {
                if (element.hasChildren())
                    indentForExpansionGifs = true;
            }
            out.print("<table class=\"labkey-nav-tree\">");
            for (NavTree element : _elements)
                renderLinks(element, 0, "", element.getId(), getViewContext(), out, indentForExpansionGifs);
            out.print("</table>");
        }
    }

    private void renderLinks(NavTree nav, int level, String pathToHere, String rootId,
                             ViewContext context, PrintWriter out, boolean indentForExpansionGifs) throws URISyntaxException
    {
        Container c = context.getContainer();
        ActionURL currentUrl = context.getActionURL();
        if (c.isWorkbook())
        {
            currentUrl = currentUrl.clone();
            currentUrl.setPath(currentUrl.getParsedPath().getParent());
        }
        if (c.isWorkbook())
            c = c.getParent();
        ActionURL startURL = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c);
        String pattern = startURL.getLocalURIString();

        String link = nav.getValue() == null ? null : nav.getValue();
        String onClickScript = (null != nav.getScript()) ? PageFlowUtil.filter(nav.getScript()) : null;
        if(null == onClickScript && null != link)
            onClickScript = "window.location='" + PageFlowUtil.filter(link) + "'; return false;";
        boolean selected = _highlightSelection && null != link && matchPath(link, currentUrl, pattern);
        if (level == 0 && null != nav.getKey())
            level = 1;

        boolean hasChildren = nav.getChildren() != null && nav.getChildren().length > 0;
        if (null != nav.getKey())
            //When we post the expanded path, we need to use the escaped key so that embedded
            // '/' characters in the key are not confused with path separators
            pathToHere = pathToHere + "/" + nav.getEscapedKey();

        boolean collapsed = nav.isCollapsed();

        if (level > 0)
        {
            out.print("<tr class=\"labkey-nav-tree-row labkey-header\">");

            out.print("<td class=\"labkey-nav-tree-node\">\n");

            if (hasChildren)
            {
                ActionURL expandCollapseUrl = PageFlowUtil.urlProvider(ProjectUrls.class). getExpandCollapseURL(getViewContext().getContainer(), pathToHere, rootId);

                String image = collapsed ? "plus.gif" : "minus.gif";
                out.printf("<a href=\"%s\" onclick=\"return toggleLink(this, %s);\">",
                        filter(expandCollapseUrl),
                        "true");

                out.printf("<img src=\"%s/_images/%s\"></a>", context.getContextPath(), image);
            }
            else if (indentForExpansionGifs)
                out.printf("<div class=\"labkey-nav-tree-indenter\"></div>");

            out.print("</td><td class=\"labkey-nav-tree-text\"");
            if(null != onClickScript)
                out.print(" onclick=\"" + onClickScript + "\"");
            out.println(">");

            if (null == link)
                out.print(filter(nav.getKey()));
            else
            {
                if (!StringUtils.isEmpty(nav.getId()))
                    out.printf("<a id=\"%s\" href=\"%s\"", filter(nav.getId()), filter(link));
                else
                    out.printf("<a href=\"%s\"", filter(link));
                if (selected)
                {
                    out.printf(" style=\"%s\"", "font-weight:bold;font-style:italic");
                }
                if (null != nav.getScript())
                {
                    out.print(" onclick=\"");
                    out.print(filter(nav.getScript()));
                    out.print("\"");
                }
                out.print(">");
                out.print(filter(nav.getKey()));
                out.print("</a>");
                if (selected)
                    out.printf("&nbsp;<img src=\"%s/_images/square.gif\">", context.getContextPath());
            }

            out.print("</td></tr>");
        }

        //Render children as nested table in a row...
        if (hasChildren)
        {
            for (NavTree element : nav.getChildren())
            {
                if (element.hasChildren())
                    indentForExpansionGifs = true;
            }
            out.printf("<tr%s>\n<td></td><td>\n<table class=\"labkey-nav-tree-child\">", collapsed ? " style=display:none" : "");
            for (NavTree child : nav.getChildren())
                renderLinks(child, level + 1, pathToHere, rootId, context, out, indentForExpansionGifs);
            out.println("</table>\n</td></tr>");
        }
    }

    protected boolean matchPath(String link, ActionURL currentUrl, String pattern)
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
