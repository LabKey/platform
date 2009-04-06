/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.HString;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.labkey.wiki.BaseWikiPermissions;
import org.labkey.wiki.WikiController;
import org.labkey.wiki.WikiManager;

/**
 * User: Mark Igra
 * Date: Jun 12, 2006
 * Time: 3:29:31 PM
 */

abstract class BaseWikiView extends JspView<Object>
{
    String _pageId = null;
    int _index = 0;
    Wiki _wiki = null;
    WikiVersion _wikiVersion = null;

    protected BaseWikiView()
    {
        super("/org/labkey/wiki/view/wiki.jsp");
        addObject("hasReadPermission", Boolean.TRUE);
        addObject("hasAdminPermission", Boolean.FALSE);
        addObject("hasInsertPermission", Boolean.FALSE);
        addObject("updateContentLink", null);
        addObject("insertLink", null);
        addObject("manageLink", null);
        addObject("versionsLink", null);
        addObject("wiki", null);
        addObject("wikiversion", null);
        addObject("redirect", "");
        addObject("wikiPageCount", 0);
        addObject("customizeLink", null);
        addObject("printLink", null);
        addObject("startDiscussion", Boolean.FALSE);
        addObject("hasContent", Boolean.TRUE);
        addObject("includeLinks", Boolean.TRUE);
        addObject("isEmbedded", Boolean.FALSE);
    }


    // Was prepareWebPart -- now moving all initialization to creation time.  e.g., Portal.populatePortalView needs to see title hrefs before prepare.
    // TODO: Refactor this into Base, WikiView, and WikiWebPart (e.g., eliminate isInWebPart)
    protected void init(Container c, HString name, boolean isInWebPart)
    {
        ViewContext context = getViewContext();
        User user = context.getUser();
        ActionURL url = context.getActionURL();

        BaseWikiPermissions perms = new BaseWikiPermissions(user, c);

        context.put("isInWebPart", isInWebPart);

        //current number of pages in container
        int pageCount = WikiManager.getPageList(c).size();
        context.put("wikiPageCount", pageCount);

        //set initial page title
        HString title;
        if (isInWebPart)
            title = new HString("Wiki Web Part");
        else
        {
            if (pageCount == 0)
                title = new HString("Wiki");
            else
                title = name;
        }

        if (name == null)
        {
            _wiki = new Wiki(c, new HString("default",false));
            addObject("hasContent", false);
        }
        else
        {
            if (null == _wiki)
                _wiki = WikiManager.getWiki(c, name);

            //this is a non-existent wiki
            if (null == _wiki)
            {
                _wiki = new Wiki(c, name);
                addObject("hasContent", false); 
            }

            assert _wiki.getName() != null;

            if (null == _wikiVersion)
                _wikiVersion = WikiManager.getLatestVersion(_wiki);

            if (null == _wikiVersion)
                _wikiVersion = new WikiVersion(name);

            String html;

            if(perms.allowRead(_wiki))
            {
                try
                {
                    html = _wikiVersion.getHtml(c, _wiki);
                }
                catch (Exception e)
                {
                    Logger.getLogger(BaseWikiView.class).error("Error generating HTML for wiki page "
                            + _wiki.getName() + " in container " + _wiki.getContainerPath(), e);

                    //build HTML that displays the exception text
                    //so the user can still get to the [edit] and other links
                    StringBuilder userMsg = new StringBuilder("<p class='labkey-error'><b>An Exception occurred while generating the HTML for this wiki page:</b></p>");
                    userMsg.append("<p>");

                    String exceptionMsg = e.toString();
                    exceptionMsg = exceptionMsg.replace("\n", "<br/>");

                    userMsg.append(exceptionMsg);
                    userMsg.append("</p>");

                    html = userMsg.toString();
                }
            }
            else
                html = ""; //wiki.jsp will display appropriate message if user doesn't have read perms

            context.put("wiki", _wiki);
            context.put("name", _wiki.getName());
            context.put("formattedHtml", html);

            //set title if page has content and user has permission to see it
            if (html != null && perms.allowRead(_wiki))
                title = getTitle() == null ? _wikiVersion.getTitle() : new HString(getTitle());

            //what does "~" represent?
            if (!_wiki.getName().startsWith("~"))
            {
                if (perms.allowUpdate(_wiki))
                {
                    context.put("manageLink", _wiki.getManageLink());
                }
            }
        }

        context.put("hasReadPermission", Boolean.valueOf(perms.allowRead(_wiki)));
        context.put("hasAdminPermission", Boolean.valueOf(perms.allowAdmin()));
        context.put("hasInsertPermission", Boolean.valueOf(perms.allowInsert()));

        if (perms.allowInsert())
        {
            ActionURL insertLink = new ActionURL(WikiController.EditWikiAction.class, c);
            insertLink.addParameter("cancel", getViewContext().getActionURL().getLocalURIString());
            if (isInWebPart)
            {
                insertLink.addParameter("redirect", url.getLocalURIString());
                insertLink.addParameter("pageId", _pageId);
                insertLink.addParameter("index", Integer.toString(_index));
            }
            if(null != _wiki)
                insertLink.addParameter("defName", _wiki.getName());
            context.put("insertLink", insertLink.toString());

        }

        if (perms.allowUpdate(_wiki))
        {
            String versionsLink = _wiki.getVersionsLink();
            context.put("versionsLink", versionsLink);

            ActionURL updateContentLink =  new ActionURL(WikiController.EditWikiAction.class, c);
            updateContentLink.addParameter("cancel", getViewContext().getActionURL().getLocalURIString());

            updateContentLink.addParameter("name", _wiki.getName());
            updateContentLink.addParameter("redirect", url.getLocalURIString());
            context.put("updateContentLink", updateContentLink);
        }

        if (isInWebPart)
        {
            ActionURL customizeUrl = url.clone();
            customizeUrl.setAction("customizeWebPart");
            customizeUrl.addParameter("pageId", _pageId);
            customizeUrl.addParameter("index", Integer.toString(_index));
            context.put("customizeLink", customizeUrl.toString());
            setTitleHref(WikiController.getPageURL(_wiki, c));
        }

        if (!isInWebPart || perms.allowInsert())
        {
            if (null == context.getRequest().getParameter("_print"))
                context.put("printLink", _wiki.getPageLink() + "&_print=1");
        }

        setTitle(title.getSource());
    }
}
