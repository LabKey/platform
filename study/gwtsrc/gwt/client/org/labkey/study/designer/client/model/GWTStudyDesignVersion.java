/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

package gwt.client.org.labkey.study.designer.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

import java.util.Date;

/**
 * User: Mark Igra
 * Date: Feb 15, 2007
 * Time: 9:17:41 PM
 */
public class GWTStudyDesignVersion implements IsSerializable
{
    private int studyId;
    private Date created;
    private int revision;
    private boolean draft;
    private String label;
    private String description;
    private String writerName;
    private boolean saveSuccessful;
    private String errorMessage;

    public int getStudyId()
    {
        return studyId;
    }

    public void setStudyId(int studyId)
    {
        this.studyId = studyId;
    }

    public Date getCreated()
    {
        return created;
    }

    public void setCreated(Date created)
    {
        this.created = created;
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

    public void setWriterName(String writerName)
    {
        this.writerName = writerName;
    }

    public String getWriterName()
    {
        return writerName;
    }

    public boolean isSaveSuccessful()
    {
        return saveSuccessful;
    }

    public void setSaveSuccessful(boolean saveSuccessful)
    {
        this.saveSuccessful = saveSuccessful;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }
}
