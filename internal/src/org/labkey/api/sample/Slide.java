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
