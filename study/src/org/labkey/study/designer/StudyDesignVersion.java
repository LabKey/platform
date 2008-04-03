package org.labkey.study.designer;

import org.labkey.api.data.Container;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.study.designer.client.model.GWTStudyDesignVersion;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Feb 12, 2007
 * Time: 10:44:20 AM
 * To change this template use File | Settings | File Templates.
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
            gwtVersion.setWriterName(user.getDisplayName(context));

        gwtVersion.setCreated(new Date(getCreated().getTime()));
        gwtVersion.setStudyId(getStudyId());
        gwtVersion.setRevision(getRevision());
        gwtVersion.setSaveSuccessful(true);

        return gwtVersion;
    }
}
