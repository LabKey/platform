/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Entity;
import org.labkey.api.view.ActionURL;
import org.labkey.wiki.WikiController;
import org.labkey.wiki.WikiController.ManageAction;
import org.labkey.wiki.WikiController.PageAction;
import org.labkey.wiki.WikiController.VersionsAction;
import org.labkey.wiki.WikiSelectManager;
import org.springframework.web.servlet.mvc.Controller;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * User: mbellew
 * Date: Jan 13, 2005
 * Time: 10:28:24 AM
 */

public class Wiki extends Entity implements Serializable
{
    // TODO: it's odd we need all of entityId, rowId and name
    // entityId for attachments
    // rowId for update form (it's the PK)
    // name because that's what's on the URL

    private int _rowId;
    private String _name;
    private int _parent = -1;
    private float _displayOrder = 0;
    private Integer _pageVersionId;
    private boolean _showAttachments = true;
    private boolean _shouldIndex = true;

    public Wiki()
    {
    }


    public Wiki(Container c, String name)
    {
        setContainerId(c.getId());
        _name = name;
    }


    public ActionURL getWikiURL(Class<? extends Controller> actionClass, String name)
    {
        return WikiController.getWikiURL(lookupContainer(), actionClass, name);
    }


    public ActionURL getPageURL()
    {
        return getWikiURL(PageAction.class, _name);
    }


    public @Nullable ActionURL getVersionsURL()
    {
        if (null == _name)
            return null;
        return getWikiURL(VersionsAction.class, _name);
    }


    public ActionURL getManageURL()
    {
        if (null == _name)
            return null;

        return getWikiURL(ManageAction.class, _name);
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
        return WikiSelectManager.getWiki(ContainerManager.getForId(getContainerId()), getParent());
    }

    public int getParent()
    {
        return _parent;
    }

    public void setParent(int parent)
    {
        _parent = parent;
    }


    public String getName()
    {
        return _name;
    }


    public void setName(String name)
    {
        _name = name;
    }

    public WikiVersion getLatestVersion()
    {
        if (null == getPageVersionId())
            return null;

        return WikiSelectManager.getVersion(lookupContainer(), getPageVersionId());
    }

    public Collection<Attachment> getAttachments()
    {
        return AttachmentService.get().getAttachments(new WikiAttachmentParent(this));
    }

    public boolean hasChildren()
    {
        return WikiSelectManager.hasChildren(lookupContainer(), getRowId());
    }

    public List<Wiki> children()
    {
        return getRowId() > 0 ? WikiSelectManager.getChildWikis(lookupContainer(), getRowId()) : null;
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

    public boolean isShouldIndex()
    {
        return _shouldIndex;
    }

    public void setShouldIndex(boolean shouldIndex)
    {
        _shouldIndex = shouldIndex;
    }


    @Override
    public String toString()
    {
        return "Wiki: \"" + getName() + "\" (rowId:" + _rowId + ")";
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Wiki wiki = (Wiki) o;

        return _rowId == wiki._rowId;
    }

    @Override
    public int hashCode()
    {
        return _rowId;
    }

    public AttachmentParent getAttachmentParent()
    {
        return new WikiAttachmentParent(this);
    }
}
