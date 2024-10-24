/*
 * Copyright (c) 2010-2019 LabKey Corporation
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
package org.labkey.announcements.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.announcements.model.AnnouncementModel;
import org.labkey.api.announcements.api.Announcement;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.wiki.WikiRendererType;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * User: Nick
 * Date: Jun 30, 2010
 * Time: 6:11:07 PM
 */
public class AnnouncementImpl implements Announcement
{
    AnnouncementModel _model;

    public AnnouncementImpl(AnnouncementModel model)
    {
        _model = model;
    }

    @Override
    public Collection<Attachment> getAttachments()
    {
        return _model.getAttachments();
    }

    @Override
    public Date getExpires()
    {
        return _model.getExpires();
    }

    public void setExpires(Date expires)
    {
        _model.setExpires(expires);
    }

    @Override
    public int getRowId()
    {
        return _model.getRowId();
    }

    public void setRowId(int rowId)
    {
        _model.setRowId(rowId);
    }

    @Override
    public String getEntityId()
    {
        return _model.getEntityId();
    }

    @Override
    public String getParent()
    {
        return _model.getParent();
    }

    @Override
    public String getTitle()
    {
        return _model.getTitle();
    }

    public void setTitle(String title)
    {
        _model.setTitle(title);
    }

    @Override
    public String getBody()
    {
        return _model.getBody();
    }
    
    public void setBody(String body)
    {
        _model.setBody(body);
    }

    public void setContainer(Container container)
    {
        _model.setContainer(container.getId());
    }

    @Override
    public Container getContainer()
    {
        return _model.lookupContainer();
    }

    @Override
    public String getStatus()
    {
        return _model.getStatus();
    }

    public void setStatus(String status)
    {
        _model.setStatus(status);
    }

    @Override
    public Date getCreated()
    {
        return _model.getCreated();
    }

    public void setCreated(Date created)
    {
        _model.setCreated(created);
    }

    @Override
    public int getCreatedBy()
    {
        return _model.getCreatedBy();
    }

    @Override
    public Date getModified()
    {
        return _model.getModified();
    }

    public void setModified(Date modified)
    {
        _model.setModified(modified);
    }

    @Override
    public int getModifiedBy()
    {
        return _model.getModifiedBy();
    }

    @Override
    public WikiRendererType getRendererType()
    {
        return WikiRendererType.valueOf(_model.getRendererType()); 
    }

    public void setRendererType(WikiRendererType rendererType)
    {
        _model.setRendererType(rendererType.getDisplayName());
    }

    @Override
    public @NotNull List<Integer> getMemberListIds()
    {
        return _model.getMemberListIds();
    }

    // This needs to be filled out more completely
}
