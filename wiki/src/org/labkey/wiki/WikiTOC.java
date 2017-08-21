/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NavTreeManager;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.menu.NavTreeMenu;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.wiki.model.Wiki;

import java.io.PrintWriter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Stack;

public class WikiTOC extends NavTreeMenu
{
    private String _selectedLink;
    private final Container _cToc;

    public WikiTOC(ViewContext context)
    {
        this(context, null);
    }

    public WikiTOC(ViewContext context, @Nullable Portal.WebPart part)
    {
        super(context, "");
        setFrame(FrameType.PORTAL);

        //set specified web part title
        String title = "Pages";
        if (null != part && part.getPropertyMap().get("title") != null)
        {
            title = part.getPropertyMap().get("title");
        }
        setTitle(title);

        // get stored property value for source container for toc
        String id = (null != part ? part.getPropertyMap().get("webPartContainer") : null);

        // if no value is stored, use the current container
        if (id == null)
        {
            _cToc = context.getContainer();
        }
        else
        {
            _cToc = ContainerManager.getForId(id);
        }

        if (null == _cToc)
            throw new NotFoundException("Could not find container for id: \"" + id + "\"");

        setId(getNavTreeId(_cToc));
        setElements(context, getNavTree());
        setCollapsible(false);
        setNavMenu(createNavMenu());
    }

    private NavTree createNavMenu()
    {
        ViewContext context = getViewContext();
        User user = context.getUser();

        //output only this one if wiki contains no pages
        boolean bHasInsert = _cToc.hasPermission("WikiTOC.getNavMenu()", user, InsertPermission.class);
        boolean bHasCopy = _cToc.hasPermission("WikiTOC.getNavMenu()", user, AdminPermission.class) && getElements().size() > 0;
        boolean bHasPrint = (bHasInsert || !isInWebPart(context)) && getElements().size() > 0;

        NavTree menu = new NavTree();
        if (bHasInsert)
        {
            ActionURL newPageUrl = new ActionURL(WikiController.EditWikiAction.class, _cToc);
            newPageUrl.addParameter("cancel", context.getActionURL().getLocalURIString());
            menu.addChild("New", newPageUrl.getLocalURIString());
        }
        if (bHasCopy)
        {
            URLHelper copyUrl = new ActionURL(WikiController.CopyWikiLocationAction.class, _cToc);
            //pass in source container as a param.
            copyUrl.addParameter("sourceContainer", _cToc.getPath());
            menu.addChild("Copy", copyUrl.toString());
        }
        if (bHasPrint)
        {
            menu.addChild("Print all", new ActionURL(WikiController.PrintAllAction.class, _cToc).toString());
        }
        return menu;
    }

    @Override
    public void enableExpandCollapse(String rootId, boolean collapsed)
    {
        addObject("collapsed", false);
        addObject("rootId", rootId);
    }

    public static String getNavTreeId(Container cToc)
    {
        return "Wiki-TOC-" + cToc.getId();
    }

    private List<NavTree> getNavTree()
    {
        return WikiSelectManager.getNavTree(_cToc, getViewContext().getUser());
    }

    private Wiki findSelectedPage(ViewContext context)
    {
        //are there pages in the TOC container?
        if (WikiSelectManager.hasPages(_cToc))
        {
            //determine current page
            String pageViewName = context.getRequest().getParameter("name");

            //if no current page, determine the default page for the toc container
            if (null == pageViewName)
                pageViewName = WikiController.getDefaultPage(_cToc).getName();

            if (null != pageViewName)
                return WikiSelectManager.getWiki(_cToc, pageViewName);
        }

        return null;
    }

    @Override
    protected boolean matchPath(String link, ActionURL currentUrl, String pattern)
    {
        return _selectedLink != null && link.compareToIgnoreCase(_selectedLink) == 0;
    }

    @NotNull
    @Override
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        // add dependent client-side scripts
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("wiki/internal/Wiki.js"));
        return resources;
    }

    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        ViewContext context = getViewContext();

        boolean isInWebPart = isInWebPart(context);

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
            String path = "";
            Wiki page = selectedPage;
            Stack<String> stkPages = new Stack<>();

            page = page.getParentWiki();

            while (null != page)
            {
                stkPages.push(page.getLatestVersion().getTitle());
                page = page.getParentWiki();
            }

            while (!stkPages.empty())
            {
                path = path + "/" + NavTree.escapeKey(stkPages.pop());
                NavTree node = root.findSubtree(path);
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
            List<String> nameList = WikiSelectManager.getPageNames(_cToc);

            if (nameList.contains(selectedPage.getName()))
            {
                //determine where this page is in the ordered wiki page list
                int pageIndex = nameList.indexOf(selectedPage.getName());

                //if it's not the first page in the list, display the previous link
                if (pageIndex > 0)
                {
                    prevURL = WikiController.getPageURL(_cToc, nameList.get(pageIndex - 1));
                }

                //if it's not the last page in the list, display the next link
                if (pageIndex < nameList.size() - 1)
                {
                    nextURL = WikiController.getPageURL(_cToc, nameList.get(pageIndex + 1));
                }
            }
        }

        out.println("<div id=\"NavTree-"+ getId() +"\">");
        super.renderView(model, out);
        out.println("</div>");

        if (getElements().size() > 1)
        {
            out.println("<br>");
            out.println("<table width=\"100%\">");
            out.println("<tr>\n<td>");

            if (prevURL != null)
            {
                out.print(PageFlowUtil.textLink("previous", prevURL));
            }

            if (nextURL != null)
            {
                out.print(PageFlowUtil.textLink("next", nextURL));
            }

            if (showExpandOption)
            {
                out.println("</td></tr><tr><td>&nbsp;</td></tr><tr><td>");
                out.println(PageFlowUtil.textLink("expand all", "javascript:void(0);", "LABKEY.wiki.internal.Wiki.adjustAllTocEntries('NavTree-" + getId() + "', true, true)", ""));
                out.println(PageFlowUtil.textLink("collapse all", "javascript:void(0);", "LABKEY.wiki.internal.Wiki.adjustAllTocEntries('NavTree-" + getId() + "', true, false)", ""));
            }

            out.println("</td>\n</tr>\n</table>");
        }
    }

    private boolean isInWebPart(ViewContext context)
    {
        //is page being rendered in web part or in module?
        return context.getActionURL().getController().equalsIgnoreCase("Project");
    }
}
