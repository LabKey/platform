package org.labkey.study.model;

import org.labkey.api.data.Container;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Aug 17, 2006
 * Time: 12:56:46 PM
 */
public class UploadLog
{
    private int rowId;
    private Container container;
    private String entityId;
    private Date created;
    private int userId;
    private String description;
    private String filePath;
    private int datasetId;
    private String status;

    public Container getContainer()
    {
        return container;
    }

    public void setContainer(Container container)
    {
        this.container = container;
    }

    public String getEntityId()
    {
        return entityId;
    }

    public void setEntityId(String entityId)
    {
        this.entityId = entityId;
    }

    public Date getCreated()
    {
        return created;
    }

    public void setCreated(Date created)
    {
        this.created = created;
    }

    public int getUserId()
    {
        return userId;
    }

    public void setUserId(int userId)
    {
        this.userId = userId;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getFilePath()
    {
        return filePath;
    }

    public void setFilePath(String filePath)
    {
        this.filePath = filePath;
    }

    public int getDatasetId()
    {
        return datasetId;
    }

    public void setDatasetId(int datasetId)
    {
        this.datasetId = datasetId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public int getRowId()
    {
        return rowId;
    }

    public void setRowId(int rowId)
    {
        this.rowId = rowId;
    }
}
