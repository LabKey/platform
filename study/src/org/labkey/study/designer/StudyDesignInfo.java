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
import org.labkey.api.exp.Lsid;

import java.util.Date;

/**
 * User: Mark Igra
 * Date: Feb 13, 2007
 * Time: 9:59:12 AM
 */
public class StudyDesignInfo
{
    private int studyId;
    private int createdBy;
    private Date created;
    private int modifiedBy;
    private Date modified;
    private Container container;
    private int publicRevision;
    private int draftRevision;
    private String label;
    private Container sourceContainer;
    private boolean active;

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

    public int getModifiedBy()
    {
        return modifiedBy;
    }

    public void setModifiedBy(int modifiedBy)
    {
        this.modifiedBy = modifiedBy;
    }

    public Date getModified()
    {
        return modified;
    }

    public void setModified(Date modified)
    {
        this.modified = modified;
    }

    public Container getContainer()
    {
        return container;
    }

    public void setContainer(Container container)
    {
        this.container = container;
        if (null == sourceContainer)
            this.sourceContainer = container;
    }

    public int getPublicRevision()
    {
        return publicRevision;
    }

    public void setPublicRevision(int publicRevision)
    {
        this.publicRevision = publicRevision;
    }

    public int getDraftRevision()
    {
        return draftRevision;
    }

    public void setDraftRevision(int draftRevision)
    {
        this.draftRevision = draftRevision;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public Lsid getLsid()
    {
        return new Lsid("study-design", String.valueOf(getStudyId()));
    }

    public Container getSourceContainer() {
        return sourceContainer;
    }

    public void setSourceContainer(Container sourceContainer) {
        this.sourceContainer = sourceContainer;
    }

    public boolean isActive()
    {
        return active;
    }

    public void setActive(boolean active)
    {
        this.active = active;
    }
}
