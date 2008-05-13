/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.sample;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.AttachmentParentEntity;

import java.sql.SQLException;

/**
 * User: Mark Igra
 * Date: Nov 18, 2006
 * Time: 2:27:35 PM
 */
public class Slide extends AttachmentParentEntity
{
    private int rowId;
    private String sampleLSID;
    private int stainId;
    private String notes;

    public int getRowId()
    {
        return rowId;
    }

    public void setRowId(int rowId)
    {
        this.rowId = rowId;
    }

    public String getSampleLSID()
    {
        return sampleLSID;
    }

    public void setSampleLSID(String sampleLSID)
    {
        this.sampleLSID = sampleLSID;
    }

    public int getStainId()
    {
        return stainId;
    }

    public void setStainId(int stainId)
    {
        this.stainId = stainId;
    }

    public String getNotes()
    {
        return notes;
    }

    public void setNotes(String notes)
    {
        this.notes = notes;
    }

    public Attachment getSlideImage() throws SQLException
    {
        if (null == this.getEntityId())
            return null;
        
        Attachment[] attachments = AttachmentService.get().getAttachments(this);
        if (null == attachments || attachments.length == 0)
            return null;

        return attachments[0];
    }
}
