package org.labkey.announcements.api;

import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.announcements.model.AnnouncementModel;
import org.labkey.api.announcements.api.Announcement;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.wiki.WikiRendererType;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;

/**
 * Created by IntelliJ IDEA.
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

    public Collection<Attachment> getAttachments()
    {
        Collection<Attachment> attachments;
        try
        {
            attachments = _model.getAttachments();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        return attachments;
    }

    public void setAttachments(Collection<Attachment> attachments)
    {
        _model.setAttachments(attachments);
    }

    public Date getExpires()
    {
        return _model.getExpires();
    }

    public void setExpires(Date expires)
    {
        _model.setExpires(expires);
    }

    public int getRowId()
    {
        return _model.getRowId();
    }

    public void setRowId(int rowId)
    {
        _model.setRowId(rowId);
    }

    public String getTitle()
    {
        return _model.getTitle();
    }

    public void setTitle(String title)
    {
        _model.setTitle(title);
    }

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

    public Container getContainer()
    {
        return _model.lookupContainer();
    }

    public String getStatus()
    {
        return _model.getStatus();
    }

    public void setStatus(String status)
    {
        _model.setStatus(status);
    }

    public Date getCreated()
    {
        return _model.getCreated();
    }

    public void setCreated(Date created)
    {
        _model.setCreated(created);
    }

    public Date getModified()
    {
        return _model.getModified();
    }

    public void setModified(Date modified)
    {
        _model.setModified(modified);
    }

    public WikiRendererType getRendererType()
    {
        return WikiRendererType.valueOf(_model.getRendererType()); 
    }

    public void setRendererType(WikiRendererType rendererType)
    {
        _model.setRendererType(rendererType.getDisplayName());
    }
    
    // This needs to be filled out more completely
}
