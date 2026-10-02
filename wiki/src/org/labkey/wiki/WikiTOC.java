/*
 * Copyright (c) 2010-2026 LabKey Corporation
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

import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AbstractContainerScopingTest;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.SubmitterRole;
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NavTreeManager;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.menu.NavTreeMenu;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.writer.HtmlWriter;
import org.labkey.wiki.model.Wiki;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Stack;

import static org.labkey.api.util.DOM.Attribute.id;
import static org.labkey.api.util.DOM.Attribute.width;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.TABLE;
import static org.labkey.api.util.DOM.TD;
import static org.labkey.api.util.DOM.TR;
import static org.labkey.api.util.DOM.at;

public class WikiTOC extends NavTreeMenu
{
    private String _selectedLink;
    private final Container _cToc;
    private final boolean _canRead;

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

        // Check permissions here just to skip work below. Throwing UnauthorizedException here would be fine, but the
        // message formatting would be inconsistent with wiki webpart, etc., so render the message in renderView().
        _canRead = _cToc.hasPermission(context.getUser(), ReadPermission.class);

        if (_canRead)
        {
            setId(getNavTreeId(_cToc));
            setElements(context, getNavTree());
            setCollapsible(false);
            setNavMenu(createNavMenu());
        }
    }

    private NavTree createNavMenu()
    {
        ViewContext context = getViewContext();
        User user = context.getUser();

        //output "New" if wiki contains no pages
        boolean hasInsert = _cToc.hasPermission("WikiTOC.getNavMenu()", user, InsertPermission.class);
        boolean hasCopy = _cToc.hasPermission("WikiTOC.getNavMenu()", user, AdminPermission.class) && !getElements().isEmpty();
        // Must have update in the container since this is a folder-wide, potentially expensive operation. GitHub Issue #1415.
        boolean hasUpdate = _cToc.hasPermission("WikiTOC.getNavMenu()", user, UpdatePermission.class);
        boolean hasPrintAll = hasUpdate && !isInWebPart(context) && !getElements().isEmpty();

        NavTree menu = new NavTree();
        if (hasInsert)
        {
            ActionURL newPageUrl = new ActionURL(WikiController.EditWikiAction.class, _cToc);
            newPageUrl.addParameter("cancel", context.getActionURL().getLocalURIString());
            menu.addChild("New", newPageUrl.getLocalURIString());
        }
        if (hasCopy)
        {
            URLHelper copyUrl = new ActionURL(WikiController.CopyWikiLocationAction.class, _cToc);
            //pass in source container as a param.
            copyUrl.addParameter("sourceContainer", _cToc.getPath());
            menu.addChild("Copy", copyUrl.toString());
        }
        if (hasPrintAll)
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
    protected void renderView(Object model, HtmlWriter out)
    {
        ViewContext context = getViewContext();
        User user = context.getUser();

        // Check read permission in target container before rendering anything, GH Issue 1445
        if (!_canRead)
        {
            out.write(WikiManager.get().getNoPermissionsMessage(user));
            return;
        }

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
        //NOTE: This is an artifact of the detail that we can't use the
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

        DIV(
            at(id, "NavTree-"+ getId()),
            (DOM.Renderable) ret -> {
                try
                {
                    super.renderView(model, out);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
                return ret;
            }
        ).appendTo(out);

        if (getElements().size() > 1)
        {
            out.write(HtmlString.BR);
            TABLE(
                at(width, "100%"),
                TR(
                    TD(
                        prevURL != null ? LinkBuilder.labkeyLink("previous", prevURL) : null,
                        nextURL != null ? LinkBuilder.labkeyLink("next", nextURL) : null
                    )
                ),
                showExpandOption ? TR(TD(HtmlString.NBSP)) : null,
                showExpandOption ? TR(TD(
                    LinkBuilder.labkeyLink("expand all").onClick("LABKEY.wiki.internal.Wiki.adjustAllTocEntries('NavTree-" + getId() + "', true, true)"),
                    LinkBuilder.labkeyLink("collapse all").onClick("LABKEY.wiki.internal.Wiki.adjustAllTocEntries('NavTree-" + getId() + "', true, false)")
                )) : null
            ).appendTo(out);
        }
    }

    private boolean isInWebPart(ViewContext context)
    {
        //is page being rendered in web part or in module?
        return context.getActionURL().getController().equalsIgnoreCase("Project");
    }

    public static class TestCase extends AbstractContainerScopingTest
    {
        private static final String PAGE_TITLE = "WikiTocTargetPage";
        private static final String NEW_MENU_ITEM = ">New</a>";

        private Container _host;
        private Container _target;

        @Before
        public void createFolders()
        {
            _host = createContainer("Host");
            _target = createContainer("Target");
            WikiManager.get().insertWiki(getAdmin(), _target, "tocPage", "body", WikiRendererType.HTML, PAGE_TITLE);
        }

        @Test
        public void testTocRequiresReadInTargetFolder() throws Exception
        {
            User user = createUserInRole(_host, ReaderRole.class);
            String html = renderToc(user);
            assertTrue("Expected no-permission message, html was: " + html, html.contains(WikiManager.get().getNoPermissionsMessage(user).toString()));
            assertFalse("Target folder's page leaked into the TOC", html.contains(PAGE_TITLE));

            grantRole(user, _target, ReaderRole.class);
            html = renderToc(user);
            assertTrue("Reader in the target folder should see its pages, html was: " + html, html.contains(PAGE_TITLE));

            MutableSecurityPolicy policy = new MutableSecurityPolicy(_host.getPolicy());
            policy.addRoleAssignment(SecurityManager.getGroup(Group.groupGuests), ReaderRole.class);
            SecurityPolicyManager.savePolicyForTests(policy, getAdmin());
            html = renderToc(User.guest);
            assertTrue("Expected guest login prompt, html was: " + html, html.contains("Please log in to see this data."));
            assertFalse("Target folder's page leaked into the guest TOC", html.contains(PAGE_TITLE));
        }

        @Test
        public void testTocHidesMenuWithoutReadInTargetFolder() throws Exception
        {
            // Submitter has Insert but not Read, so it would otherwise get the "New" menu item
            User user = createUserInRole(_host, ReaderRole.class);
            grantRole(user, _target, SubmitterRole.class);
            String html = renderToc(user);
            assertFalse("Menu should be suppressed without read, html was: " + html, html.contains(NEW_MENU_ITEM));

            grantRole(user, _target, ReaderRole.class);
            html = renderToc(user);
            assertTrue("Insert + read in the target folder should show the \"New\" menu item, html was: " + html, html.contains(NEW_MENU_ITEM));
        }

        private String renderToc(User user) throws Exception
        {
            ActionURL url = new ActionURL("project", "getWebPart", _host)
                .addParameter("webpart.name", "Wiki Table of Contents")
                .addParameter("webPartContainer", _target.getId());
            MockHttpServletResponse response = get(url, user);
            assertStatus(HttpServletResponse.SC_OK, response);
            return new JSONObject(response.getContentAsString()).getString("html");
        }
    }
}
