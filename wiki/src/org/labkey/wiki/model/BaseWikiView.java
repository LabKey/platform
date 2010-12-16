/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.util.HString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.labkey.wiki.BaseWikiPermissions;
import org.labkey.wiki.WikiSelectManager;
import org.labkey.wiki.WikiController;
import org.labkey.wiki.WikiManager;

import java.sql.SQLException;

/**
 * User: Mark Igra
 * Date: Jun 12, 2006
 * Time: 3:29:31 PM
 */

public abstract class BaseWikiView extends JspView<Object>
{
    public Wiki wiki;
    public String html;
    public boolean hasContent = true;
    public boolean hasAdminPermission;
    public boolean hasInsertPermission;
    public boolean folderHasWikis;

    public ActionURL insertURL = null;
    public ActionURL versionsURL = null;
    public ActionURL updateContentURL = null;
    public ActionURL manageURL = null;
    public ActionURL customizeURL = null;
    public ActionURL printURL = null;

    protected WikiVersion wikiVersion = null; // TODO: Used internally only?  Pass to init()?

    protected String _pageId = null;
    protected int _index = 0;

    protected BaseWikiView()
    {
        super("/org/labkey/wiki/view/wiki.jsp");
    }


    protected void init(Container c, HString name)
    {
        ViewContext context = getViewContext();
        User user = context.getUser();
        ActionURL url = context.getActionURL();

        BaseWikiPermissions perms = new BaseWikiPermissions(user, c);

        //current number of pages in container
        folderHasWikis = WikiSelectManager.hasPages(c);

        //set initial page title
        HString title;

        if (isWebPart())
            title = new HString("Wiki Web Part");
        else
        {
            if (folderHasWikis)
                title = name;
            else
                title = new HString("Wiki");
        }

        if (name == null)
        {
            wiki = new Wiki(c, new HString("default", false));
            hasContent = false;
        }
        else
        {
            if (null == wiki)
                wiki = WikiSelectManager.getWiki(c, name);

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
                    html = wikiVersion.getHtml(c, wiki);
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
                catch (Exception e)
                {
                    html = "<p class='labkey-error'>Error rendering page: " + e.getMessage() + "<p>";
                }
            }
            else
                html = ""; //wiki.jsp will display appropriate message if user doesn't have read perms

            //set title if page has content and user has permission to see it
            if (html != null && perms.allowRead(wiki))
                title = getTitle() == null ? wikiVersion.getTitle() : new HString(getTitle());

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

            if (isWebPart())
            {
                insertURL.addParameter("redirect", url.getLocalURIString());
                insertURL.addParameter("pageId", _pageId);
                insertURL.addParameter("index", Integer.toString(_index));
            }

            if (null != wiki)
                insertURL.addParameter("defName", wiki.getName());
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
            customizeURL = PageFlowUtil.urlProvider(ProjectUrls.class).getCustomizeWebPartURL(c);
            customizeURL.addParameter("pageId", _pageId);
            customizeURL.addParameter("index", _index);

            setTitleHref(WikiController.getPageURL(wiki, c));
        }

        if (!isWebPart() || perms.allowInsert())
        {
            if (null == context.getRequest().getParameter("_print"))
            {
                printURL = wiki.getPageURL();
                printURL.addParameter("_print", 1);
            }
        }

        setTitle(title.getSource());
    }

    public abstract boolean isWebPart();
}
