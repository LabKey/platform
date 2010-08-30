package org.labkey.api.announcements.api;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
import org.labkey.api.wiki.WikiRendererType;

import java.util.Collection;
import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: Nick
 * Date: Jun 30, 2010
 * Time: 6:00:00 PM
 */
public interface Announcement
{
    public String getTitle();
    public String getBody();
    public Date getExpires();
    public int getRowId();
    public Container getContainer();
    public Collection<Attachment> getAttachments();
    public String getStatus();
    public Date getCreated();
    public Date getModified();
    public WikiRendererType getRendererType();
}
