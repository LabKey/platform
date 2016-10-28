/*
 * Copyright (c) 2005-2016 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.util;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;

import java.util.Collection;

/**
 * User: arauch
 * Date: Jan 18, 2005
 * Time: 10:27:03 PM
 */
public class ContainerTree
{
    private static Logger _log = Logger.getLogger(ContainerTree.class);

    private Container _root;
    private User _user;
    private Class<? extends Permission> _perm;
    private ActionURL _url;
    private String _purpose;
    private int _initialLevel = 0;

    public ContainerTree(String rootPath, User user, Class<? extends Permission> perm, ActionURL url)
    {
        init(rootPath, user, perm, url, null);
    }

    public ContainerTree(String rootPath, User user, Class<? extends Permission> perm, ActionURL url, String purpose)
    {
        init(rootPath, user, perm, url, purpose);
    }

    private void init(String rootPath, User user, Class<? extends Permission> perm, ActionURL url, @Nullable String purpose)
    {
        _root = ContainerManager.getForPath(rootPath);
        _user = user;
        _perm = perm;
        assert null != _perm;
        setUrl(url);
        _purpose = purpose;
    }


    public void setUrl(ActionURL url)
    {
        _url = url != null ? url.clone() : null;  // We're going to change the url, so clone it
    }


    public ActionURL getUrl()
    {
        return _url;
    }


    public int getInitialLevel()
    {
        return _initialLevel;
    }

    public void setInitialLevel(int initialLevel)
    {
        _initialLevel = initialLevel;
    }

    public void render(StringBuilder html)
    {
        renderChildren(html, ContainerManager.getContainerTree(_root), _root, _initialLevel);
    }


    public StringBuilder render()
    {
        StringBuilder html = new StringBuilder();
        render(html);
        return html;
    }

    public String getPurpose()
    {
        return _purpose;
    }


    protected boolean renderChildren(StringBuilder html, MultiValuedMap<Container, Container> mm, Container parent, int level)
    {
        // Hide hidden folders, unless you're an administrator
        if (!parent.shouldDisplay(_user) || parent.isWorkbookOrTab())
            return false;

        // Retrieve children first so we can prune the tree if we don't have permission to any of the children
        Collection<Container> children = mm.get(parent);
        boolean isChildAuthorized = false;
        StringBuilder childrenHtml = new StringBuilder();

        if (null != children)
        {
            for (Container child : children)
                isChildAuthorized |= renderChildren(childrenHtml, mm, child, level + 1);
        }

        boolean isAuthorized = parent.hasPermission(_user, _perm);

        if (isAuthorized || isChildAuthorized)
        {
            renderNode(html, parent, _url, isAuthorized, level);

            if (isChildAuthorized)
                html.append(childrenHtml);
        }

        return isAuthorized || isChildAuthorized;
    }


    protected void renderNode(StringBuilder html, Container c, ActionURL url, boolean isAuthorized, int level)
    {
        renderNodeStart(html, c, url, isAuthorized, level);
        renderCellContents(html, c, isAuthorized ? url : null);   // Make it a link only if authorized
        renderNodeEnd(html, c, url, isAuthorized, level);
    }


    protected void renderNodeStart(StringBuilder html, Container c, ActionURL url, boolean isAuthorized, int level)
    {
        html.append("<tr><td style=\"padding-left:");
        html.append(10 * level).append("px");
        html.append("\">");
    }


    protected void renderNodeEnd(StringBuilder html, Container c, ActionURL url, boolean isAuthorized, int level)
    {
        html.append("</td></tr>\n");
    }


    protected void renderCellContents(StringBuilder html, Container c, ActionURL url)
    {
        if (null != url)
        {
            addContainerToURL(url, c);
            html.append("<a href=\"");
            html.append(url.getEncodedLocalURIString()).append("\"");
            if (getPurpose() != null)
            {
                html.append(" id=\"").append(getPurpose()).append("/").append(PageFlowUtil.filter(c.getName())).append("\"");
            }
            html.append(">");
            html.append(PageFlowUtil.filter(c.getName()));
            html.append("</a>");
        }
        else
        {
            html.append(PageFlowUtil.filter(c.getName()));
        }
    }


    protected void addContainerToURL(ActionURL url, Container c)
    {
        url.setContainer(c);
    }
}
