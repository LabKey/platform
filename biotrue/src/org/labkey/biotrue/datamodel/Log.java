package org.labkey.biotrue.datamodel;

import java.util.Date;

public class Log
{
    int rowId;

    public int getRowId()
    {
        return rowId;
    }

    public void setRowId(int rowId)
    {
        this.rowId = rowId;
    }

    public int getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(int sessionId)
    {
        this.sessionId = sessionId;
    }

    public int getEntityId()
    {
        return entityId;
    }

    public void setEntityId(int entityId)
    {
        this.entityId = entityId;
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

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public void setTask(String task)
    {
        this.task = task;
    }

    public String getTask()
    {
        return this.task;
    }

    public String getNotes()
    {
        return notes;
    }

    public void setNodes(String notes)
    {
        this.notes = notes;
    }

    int sessionId;
    int entityId;
    long started;
    long finished;
    String task;
    String status;
    String notes;
}
