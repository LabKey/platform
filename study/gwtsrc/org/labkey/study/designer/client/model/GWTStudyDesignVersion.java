package org.labkey.study.designer.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Feb 15, 2007
 * Time: 9:17:41 PM
 * To change this template use File | Settings | File Templates.
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
