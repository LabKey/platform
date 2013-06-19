/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.study.designer;

import org.labkey.api.data.Container;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import gwt.client.org.labkey.study.designer.client.model.GWTStudyDesignVersion;

import java.util.Date;

/**
 * User: Mark Igra
 * Date: Feb 12, 2007
 * Time: 10:44:20 AM
 */
public class StudyDesignVersion
{
    private int studyId;
    private int createdBy;
    private Date created;
    private Container container;
    private int revision;
    private boolean draft;
    private String label;
    private String description;
    private String XML;

    public int getStudyId()
    {
        return studyId;
    }

    public void setStudyId(int studyId)
    {
        this.studyId = studyId;
    }

    public int getCreatedBy()
    {
        return createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        this.createdBy = createdBy;
    }

    public Date getCreated()
    {
        return created;
    }

    public void setCreated(Date created)
    {
        this.created = created;
    }

    public Container getContainer()
    {
        return container;
    }

    public void setContainer(Container container)
    {
        this.container = container;
    }

    public int getRevision()
    {
        return revision;
    }

    public void setRevision(int revision)
    {
        this.revision = revision;
    }

    public boolean isDraft()
    {
        return draft;
    }

    public void setDraft(boolean draft)
    {
        this.draft = draft;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getXML()
    {
        return XML;
    }

    public void setXML(String XML)
    {
        this.XML = XML;
    }

    public GWTStudyDesignVersion toGWTVersion(ViewContext context)
    {
        GWTStudyDesignVersion gwtVersion = new GWTStudyDesignVersion();
        gwtVersion.setLabel(getLabel());
        User user = UserManager.getUser(getCreatedBy());
        if (null != user)
            gwtVersion.setWriterName(user.getDisplayName(context.getUser()));

        gwtVersion.setCreated(new Date(getCreated().getTime()));
        gwtVersion.setStudyId(getStudyId());
        gwtVersion.setRevision(getRevision());
        gwtVersion.setSaveSuccessful(true);

        return gwtVersion;
    }
}
