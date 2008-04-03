package org.labkey.biotrue.datamodel;

import java.util.Date;

public class Session
{
    private int rowId;

    public int getRowId()
    {
        return rowId;
    }

    public void setRowId(int rowId)
    {
        this.rowId = rowId;
    }

    public int getServerId()
    {
        return serverId;
    }

    public void setServerId(int serverId)
    {
        this.serverId = serverId;
    }

    public Date getStarted()
    {
        return new Date(started);
    }

    public void setStarted(Date started)
    {
        this.started = started.getTime();
    }

    public Date getFinished()
    {
        return new Date(finished);
    }

    public void setFinished(Date finished)
    {
        this.finished = finished.getTime();
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getDescription()
    {
        return description;
    }



    private int serverId;
    private long started;
    private long finished;
    private String description;
}
