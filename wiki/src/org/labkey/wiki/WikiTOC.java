/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.HString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NavTreeManager;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.menu.NavTreeMenu;
import org.labkey.wiki.model.Wiki;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class WikiTOC extends NavTreeMenu
{
    private String _selectedLink;

    static private Container getTocContainer(ViewContext context)
    {
        //set specified web part container
        Container cToc;
        //get stored property value for source container for toc
        Object id = context.get("webPartContainer");
        //if no value is stored, use the current container
        if (id == null)
            cToc = context.getContainer();
        else
        {
            cToc = ContainerManager.getForId(id.toString());
            assert (cToc != null) : "Could not find container for id: " + id;
        }

        return cToc;
    }

    static public String getNavTreeId(ViewContext context)
    {
        Container cToc = getTocContainer(context);
        return "Wiki-TOC-" + cToc.getId();
    }

    static public NavTree[] getNavTree(ViewContext context)
    {
        Container cToc = getTocContainer(context);
        return WikiSelectManager.getNavTree(cToc);
    }

    public WikiTOC(ViewContext context)
    {
        super(context, "");
        setFrame(FrameType.PORTAL);
        setHighlightSelection(true);

        //set specified web part title
        Object title = context.get("title");
        if (title == null)
            title = "Pages";
        setTitle(title.toString());
    }

    private Wiki findSelectedPage(ViewContext context)
    {
        Container cToc = getTocContainer(context);

        //are there pages in the TOC container?
        if (WikiSelectManager.hasPages(cToc))
        {
            //determine current page
            String pageViewName = context.getRequest().getParameter("name");

            //if no current page, determine the default page for the toc container
            if (null == pageViewName)
                pageViewName = WikiController.getDefaultPage(cToc).getName().getSource();

            if (null != pageViewName)
                return WikiSelectManager.getWiki(cToc, new HString(pageViewName));
        }

        return null;
    }

    @Override
    protected boolean matchPath(String link, ActionURL currentUrl, String pattern)
    {
        return _selectedLink != null && link.compareToIgnoreCase(_selectedLink) == 0;
    }

    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        ViewContext context = getViewContext();
        User user = context.getUser();
        setId(getNavTreeId(context));
        setElements(context, getNavTree(context));

        boolean isInWebPart = false;
        //is page being rendered in web part or in module?
        String pageUrl = context.getActionURL().getPageFlow();
        if (pageUrl.equalsIgnoreCase("Project"))
            isInWebPart = true;

        Container cToc = getTocContainer(context);

        //Should we show the option to expand all nodes?
        boolean showExpandOption = false;

        for (NavTree t : getElements())
        {
            if (t.getChildCount() != 0)
            {
                showExpandOption = true;
                break;
            }
        }

        //Generate a root node to simplify finding subtrees
        //NOTE:  This is an artifact of the detail that we can't use the
        //NavTreeMenu (this) as the root because it won't recurse into its children
        //See NavTreeMenu.findSubtree

        NavTree root = new NavTree();
        root.setId(this.getId());
        root.addChildren(getElements());

        Wiki selectedPage = findSelectedPage(context);

        //remember the link to the selected page so we can highlight it appropriately if we are not in
        //a web-part context
        if (null != selectedPage && !isInWebPart)
            _selectedLink = selectedPage.getPageURL().getLocalURIString();

        //Make sure the path to the current page is expanded
        //FIX: per 5246, we will no longer expand the children of the current page by default
        if (null != selectedPage)
        {
            HString path = HString.EMPTY;
            Wiki page = selectedPage;
            Stack<HString> stkPages = new Stack<HString>();

            page = page.getParentWiki();

            while (null != page)
            {
                stkPages.push(page.getLatestVersion().getTitle());
                page = page.getParentWiki();
            }

            while (!stkPages.empty())
            {
                path = new HString(path, "/", NavTree.escapeKey(stkPages.pop().getSource()));
                NavTree node = root.findSubtree(path.getSource());
                //Don't add it to the expand collapse set, since this would slowly collect
                //every node we've ever visited.  This way we'll only remember the state
                //if the user explicitly visits a node

                //NavTreeManager.expandCollapsePath(context, getId(), path, false);

                //Instead, we'll just expand it manually
                if (node != null)
                    node.setCollapsed(false);
            }
        }

        //Apply the current expand state
        NavTreeManager.applyExpandState(root, context);
        ActionURL nextURL = null, prevURL = null;

        if (null != selectedPage)
        {
            //get next and previous links
            List<HString> nameList = WikiSelectManager.getPageNames(cToc);

            if (nameList.contains(selectedPage.getName()))
            {
                //determine where this page is in the ordered wiki page list
                int pageIndex = nameList.indexOf(selectedPage.getName());

                //if it's not the first page in the list, display the previous link
                if (pageIndex > 0)
                {
                    prevURL = WikiController.getPageURL(cToc, nameList.get(pageIndex - 1));
                }

                //if it's not the last page in the list, display the next link
                if (pageIndex < nameList.size() - 1)
                {
                    nextURL = WikiController.getPageURL(cToc, nameList.get(pageIndex + 1));
                }
            }
        }

        //output only this one if wiki contains no pages
        boolean bHasInsert = cToc.hasPermission(user, InsertPermission.class);
        boolean bHasCopy = cToc.hasPermission(user, AdminPermission.class) && getElements().length > 0;
        boolean bHasPrint = (!isInWebPart || bHasInsert) && getElements().length > 0;

        if (bHasInsert || bHasCopy || bHasPrint)
        {
            out.println("<table class=\"labkey-wp-link-panel\">");
            out.println("<tr>");
            out.println("<td  style=\"height:16;\">");

            if (bHasInsert)
            {
                ActionURL newPageUrl = new ActionURL(WikiController.EditWikiAction.class, cToc);
                newPageUrl.addParameter("cancel", getViewContext().getActionURL().getLocalURIString());
                out.print("&nbsp;" + PageFlowUtil.textLink("new page", newPageUrl.getLocalURIString()));
            }

            if (bHasCopy)
            {
                URLHelper copyUrl = new ActionURL(WikiController.CopyWikiLocationAction.class, cToc);
                //pass in source container as a param.
                copyUrl.addParameter("sourceContainer", cToc.getPath());

                out.print("&nbsp;" + PageFlowUtil.textLink("copy pages", copyUrl.toString()));
            }

            if (bHasPrint)
            {
                Map<String, String> map = new HashMap<String, String>();
                map.put("target", "_blank");

                out.print("&nbsp;" + PageFlowUtil.textLink("print all",
                        PageFlowUtil.filter(new ActionURL(WikiController.PrintAllAction.class, cToc).toString()), null, null, map));
            }

            out.println("");
            out.println("</td></tr>");
            out.println("</table>");
        }

        out.println("<div id=\"NavTree-"+ getId() +"\">");
        super.renderView(model, out);
        out.println("</div>");

        if (getElements().length > 1)
        {
            out.println("<br>");
            out.println("<table width=\"100%\">");
            out.println("<tr>\n<td>");

            if (prevURL != null)
            {
                out.print("<a href=\"");
                out.print(PageFlowUtil.filter(prevURL));
                out.println("\">[previous]</a>");
            }
            else
            {
                out.println("[previous]");
            }

            if (nextURL != null)
            {
                out.print("<a href=\"");
                out.print(PageFlowUtil.filter(nextURL));
                out.println("\">[next]</a>");
            }
            else
            {
                out.println("[next]");
            }

            if (showExpandOption)
            {
                out.println("</td></tr><tr><td>&nbsp;</td></tr><tr><td>");
                out.println(PageFlowUtil.textLink("expand all", "javascript:;", "adjustAllTocEntries('" + getId() + "', true, true)", ""));
                out.println(PageFlowUtil.textLink("collapse all", "javascript:;", "adjustAllTocEntries('" + getId() + "', true, false)", ""));
            }

            out.println("</td>\n</tr>\n</table>");
        }
    }
}
