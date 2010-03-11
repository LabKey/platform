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
import org.labkey.api.attachments.DownloadURL;
import org.labkey.api.data.AttachmentParentEntity;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.HString;
import org.labkey.api.view.ActionURL;
import org.labkey.wiki.WikiManager;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
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

    private int rowId;
    private HString name;

    private Collection<Attachment> attachments = new ArrayList<Attachment>();

    private int parent = -1;
    private float displayOrder = 0;
    private Wiki _parentWiki;

    private Integer pageVersionId;

    private int _depth;
    private List<Wiki> _children;
    private boolean _showAttachments = true;

    protected transient ActionURL url = null;


    public Wiki()
    {
    }


    public Wiki(Container c, HString name)
    {
        setContainerId(c.getId());
        this.name = name;
    }


    public synchronized ActionURL getWikiLink(String action, HString name)
    {
        if (null == url)
            url = new ActionURL("wiki", "page", getContainerPath());
        url.setAction(action);
        if (null == name)
            url.deleteFilterParameters("name");
        else
            url.replaceParameter("name", name.getSource());
        return url.clone();
    }

    public String getDeleteLink()
    {
        if (null == name || 0 == rowId)
            return "";
        ActionURL deleteLink = getWikiLink("delete", name);
        if (0 != rowId)
            deleteLink.replaceParameter("rowId", Integer.toString(rowId));
        return deleteLink.getLocalURIString();
    }

    public String getManageLink()
    {
        if (null == name || 0 == rowId)
            return "";
        ActionURL manageLink = getWikiLink("manage", name);
        if (0 != rowId)
            manageLink.addParameter("rowId", Integer.toString(rowId));
        return manageLink.getLocalURIString();
    }

    public String getAttachmentLink(String document)
    {
        DownloadURL urlDownload = new DownloadURL("Wiki", getContainerPath(), getEntityId(), document);
        return urlDownload.getLocalURIString();
    }

    public String getVersionsLink()
    {
        if (null == name)
            return "";
        return getWikiLink("versions", name).getLocalURIString();
    }

    public String getPageLink()
    {
        return getWikiLink("page", this.name).getLocalURIString();
    }


    public int getRowId()
    {
        return rowId;
    }


    public void setRowId(int rowId)
    {
        this.rowId = rowId;
    }

    public float getDisplayOrder()
    {
        return displayOrder;
    }

    public void setDisplayOrder(float displayOrder)
    {
        this.displayOrder = displayOrder;
    }

    public Wiki getParentWiki()
    {
        if (_parentWiki == null)
            _parentWiki = WikiManager.getWikiByRowId(ContainerManager.getForId(getContainerId()), getParent());
        return _parentWiki;
    }

    public int getParent()
    {
        return parent;
    }

    public void setParent(int parent)
    {
        this.parent = parent;
    }


    public HString getName()
    {
        return name;
    }


    public void setName(HString name)
    {
        this.name = name;
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
        return attachments;
    }

    public void setAttachments(Collection<Attachment> attachments)
    {
        this.attachments = attachments;
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
        return pageVersionId;
    }

    public void setPageVersionId(Integer pageVersionId)
    {
        this.pageVersionId = pageVersionId;
    }

    public boolean isShowAttachments()
    {
        return _showAttachments;
    }

    public void setShowAttachments(boolean showAttachments)
    {
        _showAttachments = showAttachments;
    }
}
