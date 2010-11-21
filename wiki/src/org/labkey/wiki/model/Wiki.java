/*
 * Copyright (c) 2005-2010 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.DownloadURL;
import org.labkey.api.data.AttachmentParentEntity;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.HString;
import org.labkey.api.view.ActionURL;
import org.labkey.wiki.WikiController.*;
import org.labkey.wiki.WikiManager;
import org.springframework.web.servlet.mvc.Controller;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

/**
 * User: mbellew
 * Date: Jan 13, 2005
 * Time: 10:28:24 AM
 */

public class Wiki extends AttachmentParentEntity implements Serializable
{
    // TODO: it's odd we need all of entityId, rowId and name
    // entityId for attachments
    // rowId for update form (it's the PK)
    // name because that's what's on the URL

    private int _rowId;
    private HString _name;
    private int _parent = -1;
    private float _displayOrder = 0;
    private Wiki _parentWiki;
    private Integer _pageVersionId;
    private int _depth;
    private List<Wiki> _children;
    private boolean _showAttachments = true;


    public Wiki()
    {
    }


    public Wiki(Container c, HString name)
    {
        setContainerId(c.getId());
        _name = name;
    }


    public ActionURL getWikiURL(Class<? extends Controller> actionClass, HString name)
    {
        ActionURL url = new ActionURL(actionClass, lookupContainer());

        if (null == name)
            url.deleteFilterParameters("name");
        else
            url.replaceParameter("name", name.getSource());

        return url;
    }


    public String getPageLink()
    {
        return getWikiURL(PageAction.class, _name).getLocalURIString();
    }


    public String getVersionsLink()
    {
        if (null == _name)
            return "";
        return getWikiURL(VersionsAction.class, _name).getLocalURIString();
    }


    public String getDeleteLink()
    {
        if (null == _name)
            return "";
        ActionURL deleteLink = getWikiURL(DeleteAction.class, _name);

        return deleteLink.getLocalURIString();
    }


    public String getManageLink()
    {
        if (null == _name)
            return "";
        ActionURL manageLink = getWikiURL(ManageAction.class, _name);

        return manageLink.getLocalURIString();
    }


    public String getAttachmentLink(String document)
    {
        DownloadURL urlDownload = new DownloadURL(DownloadAction.class, lookupContainer(), getEntityId(), document);
        return urlDownload.getLocalURIString();
    }


    public int getRowId()
    {
        return _rowId;
    }


    @SuppressWarnings({"UnusedDeclaration"})
    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public float getDisplayOrder()
    {
        return _displayOrder;
    }

    public void setDisplayOrder(float displayOrder)
    {
        _displayOrder = displayOrder;
    }

    public Wiki getParentWiki()
    {
        if (_parentWiki == null)
            _parentWiki = WikiManager.getWikiByRowId(ContainerManager.getForId(getContainerId()), getParent());
        return _parentWiki;
    }

    public int getParent()
    {
        return _parent;
    }

    public void setParent(int parent)
    {
        _parent = parent;
    }


    public HString getName()
    {
        return _name;
    }


    public void setName(HString name)
    {
        _name = name;
    }

    public WikiVersion latestVersion()
    {
        return WikiManager.getLatestVersion(this, false);
    }

    public int versionCount() throws SQLException
    {
        return WikiManager.getVersionCount(this);
    }

    public Collection<Attachment> getAttachments()
    {
        return AttachmentService.get().getAttachments(this);
    }

    public List<Wiki> getChildren()
    {
        if (_children == null)
        {
            _children = WikiManager.getWikisByParentId(getContainerId(), getRowId());
            for (Wiki child : _children)
                child.setDepth(_depth + 1);
        }
        return _children;
    }

    public int getDepth()
    {
        return _depth;
    }

    private void setDepth(int depth)
    {
        _depth = depth;
    }

    public Integer getPageVersionId()
    {
        return _pageVersionId;
    }

    public void setPageVersionId(Integer pageVersionId)
    {
        _pageVersionId = pageVersionId;
    }

    public boolean isShowAttachments()
    {
        return _showAttachments;
    }

    public void setShowAttachments(boolean showAttachments)
    {
        _showAttachments = showAttachments;
    }

    @Override
    public String toString()
    {
        return "Wiki: \"" + getName() + "\" (rowId:" + _rowId + ")";
    }
}
