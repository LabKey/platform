/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

package org.labkey.wiki.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.wiki.BaseWikiPermissions;
import org.labkey.wiki.WikiController;
import org.labkey.wiki.WikiController.PrintBranchAction;
import org.labkey.wiki.WikiSelectManager;

import java.util.Map;

public abstract class BaseWikiView extends JspView<Object>
{
    public Wiki wiki;
    public HtmlString html;
    public boolean hasContent = true;
    public boolean hasAdminPermission;
    public boolean hasInsertPermission;
    public boolean folderHasWikis;

    public ActionURL insertURL;
    public ActionURL newURL;
    public ActionURL versionsURL;
    public ActionURL updateContentURL;
    public ActionURL manageURL;
    public ActionURL customizeURL;
    public ActionURL printURL;
    public ActionURL printBranchURL;

    protected int _webPartId = 0;

    protected BaseWikiView()
    {
        super("/org/labkey/wiki/view/wiki.jsp");
    }


    protected void init(Container c, String name, WikiVersion wikiVersion)
    {
        ViewContext context = getViewContext();
        User user = context.getUser();
        ActionURL url = context.getActionURL();

        BaseWikiPermissions perms = new BaseWikiPermissions(user, c);

        //current number of pages in container
        folderHasWikis = WikiSelectManager.hasPages(c);

        //set initial page title
        String title;

        if (isWebPart())
            title = "Wiki Web Part";
        else if (folderHasWikis)
            title = name;
        else
            title = "Wiki";

        if (name == null)
        {
            wiki = new Wiki(c, "default");
            hasContent = false;
        }
        else
        {
            if (null == wiki)
                wiki = WikiSelectManager.getWiki(c, name);

            if (null == wiki)
            {
                String aliasedName = WikiSelectManager.getNameForAlias(c, name);
                if (aliasedName != null)
                    wiki = WikiSelectManager.getWiki(c, aliasedName);
            }

            //this is a non-existent wiki
            if (null == wiki)
            {
                wiki = new Wiki(c, name);
                hasContent = false;
                wikiVersion = new WikiVersion(name);
            }

            assert wiki.getName() != null;

            if (null == wikiVersion)
                wikiVersion = wiki.getLatestVersion();

            if (perms.allowRead(wiki))
            {
                try
                {
                    // Issue 46346 - make a single call to get the HTML and dependency info to avoid double-rendering
                    html = wikiVersion.render(this, c, wiki);
                }
                catch (Exception e)
                {
                    html = HtmlString.unsafe("<p class=\"labkey-error\">Error rendering page: " + PageFlowUtil.filter(e.getMessage()) + "<p>");
                }
            }
            else
                html = HtmlString.EMPTY_STRING; //wiki.jsp will display an appropriate message if the user doesn't have read perms

            //set title if page has content and user has permission to see it
            if (html != null && perms.allowRead(wiki))
                title = getTitle() == null ? wikiVersion.getTitle() : getTitle();

            //what does "~" represent?
            if (!wiki.getName().startsWith("~"))
            {
                if (perms.allowUpdate(wiki))
                {
                    manageURL = wiki.getManageURL();
                }
            }
        }

        hasAdminPermission = perms.allowAdmin();
        hasInsertPermission = perms.allowInsert();

        if (hasInsertPermission)
        {
            insertURL = new ActionURL(WikiController.EditWikiAction.class, c);
            insertURL.addParameter("cancel", getViewContext().getActionURL().getLocalURIString());

            // This is used to return back to page where Wiki webpart is being rendered.
            if (isWebPart())
            {
                insertURL.addParameter("redirect", url.getLocalURIString());
                insertURL.addParameter("webPartId", Integer.toString(_webPartId));
            }

            if (null != wiki)
                insertURL.addParameter("defName", wiki.getName());

            newURL = new ActionURL(WikiController.EditWikiAction.class, c);
            newURL.addParameter("cancel", context.getActionURL().getLocalURIString());
        }

        if (perms.allowUpdate(wiki))
        {
            versionsURL = wiki.getVersionsURL();

            updateContentURL = new ActionURL(WikiController.EditWikiAction.class, c);
            updateContentURL.addParameter("cancel", getViewContext().getActionURL().getLocalURIString());
            updateContentURL.addParameter("name", wiki.getName());
            updateContentURL.addParameter("redirect", url.getLocalURIString());
        }

        if (isWebPart())
        {
            if (perms.allowAdmin())
            {
                // the customize URL should always be for the current container (not the wiki webpart's container)
                customizeURL = PageFlowUtil.urlProvider(ProjectUrls.class).getCustomizeWebPartURL(getViewContext().getContainer());
                customizeURL.addParameter("webPartId", _webPartId);
                customizeURL.addReturnUrl(getViewContext().getActionURL());
            }

            if (perms.allowRead(wiki))
            {
                setTitleHref(WikiController.getPageURL(wiki, c));
            }
        }

        if (null == context.getRequestOrThrow().getParameter(ActionURL.Param._print.name()))
        {
            printURL = wiki.getPageURL().addParameter(ActionURL.Param._print, 1);
            // Must have update in the container, not just owner update on the wiki, since this is a folder-wide,
            // potentially expensive operation. GitHub Issue #1415
            if (wiki.hasChildren() && c.hasPermission(user, UpdatePermission.class))
            {
                printBranchURL = new ActionURL(PrintBranchAction.class, getContextContainer()).addParameter("name", wiki.getName());
            }
        }

        // Initialize Custom Menus
        if (perms.allowUpdate(wiki) && folderHasWikis && hasContent)
        {
            boolean useInlineEditor = false;
            WikiVersion version = wiki.getLatestVersion();
            if (version != null && version.getRendererTypeEnum() == WikiRendererType.HTML && !version.hasNonVisualElements())
            {
                // get the user's editor preference
                Map<String, String> properties = PropertyManager.getProperties(user,
                        c, WikiController.SetEditorPreferenceAction.CAT_EDITOR_PREFERENCE);
                boolean useVisualEditor = !("false".equalsIgnoreCase(properties.get(WikiController.SetEditorPreferenceAction.PROP_USE_VISUAL_EDITOR)));
                if (useVisualEditor)
                    useInlineEditor = true;
            }

            if (wiki.getContainerId().equals(getViewContext().getContainer().getId()))
            {
                NavTree standardEdit = new NavTree("Edit", updateContentURL.toString(), null, "fa fa-pencil");
                addFloatingButton(standardEdit); // used by frameless webpart only

                if (useInlineEditor)
                {
                    // Include wiki.js as a client dependency only for inline editing
                    addClientDependency(ClientDependency.fromPath("internal/jQuery"));
                    addClientDependency(ClientDependency.fromPath("wiki/internal/Wiki.js"));

                    NavTree edit = new NavTree("Edit Inline", null, null, "fa fa-pencil");
                    edit.setScript("LABKEY.wiki.internal.Wiki.createWebPartInlineEditor({" +
                            "entityId: " + PageFlowUtil.jsString(wiki.getEntityId()) +
                            ",pageVersionId: " + wiki.getPageVersionId() +
                            ",name: " + PageFlowUtil.jsString(wiki.getName()) +
                            ",title: " + PageFlowUtil.jsString(wiki.getLatestVersion().getTitle()) +
                            ",rendererType: " + PageFlowUtil.jsString(wiki.getLatestVersion().getRendererTypeEnum().name()) +
                            ",parentId: " + wiki.getParent() +
                            ",showAttachments: " + wiki.isShowAttachments() +
                            ",shouldIndex: " + wiki.isShouldIndex() +
                            ",webPartId:" + _webPartId +
                            ",updateContentURL: " + PageFlowUtil.jsString(updateContentURL.toString()) +
                            "});");
                    addCustomMenu(edit);
                }
                else
                {
                    // other render types get the standard editor
                    addCustomMenu(standardEdit);
                }
            }
        }

        setTitle(title);
        setNavMenu(initNavMenu());
    }


    private NavTree initNavMenu()
    {
        NavTree menu = new NavTree("");
        if (hasContent && !(isEmbedded() && getFrame() == WebPartView.FrameType.NONE))
        {
            if (null != updateContentURL)
                menu.addChild("Edit", updateContentURL.toString(), null, "fa fa-pencil");
            if (null != newURL)
                menu.addChild("New", newURL.toString());
            //if (null != customizeURL)
            //    setCustomize(new NavTree("", customizeURL.toString()));
            if (null != manageURL)
                menu.addChild("Manage", manageURL);
            if (null != versionsURL)
            {
                NavTree history = new NavTree("History", versionsURL);
                history.setNoFollow(true);
                menu.addChild(history);
            }
            if (null != printURL)
            {
                NavTree print = new NavTree("Print", printURL);
                print.setNoFollow(true);
                menu.addChild(print);
                if (null != printBranchURL)
                    menu.addChild("Print Branch", printBranchURL);
            }
        }
        else if (!(isEmbedded() && getFrame() == WebPartView.FrameType.NONE))
        {
            // The wiki might not have been set yet -- so there is no content
            if (null != customizeURL)
                setCustomize(new NavTree("", customizeURL));
        }

        return menu;
    }
}
