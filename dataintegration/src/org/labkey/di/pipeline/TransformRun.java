package org.labkey.di.pipeline;

import org.labkey.api.security.User;

import java.util.Date;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class TransformRun
{
    private int _rowId;
    private Integer _recordCount;
    private String _transformId;
    private int _transformVersion;
    private String _status;
    private Date _startTime;
    private Date _endTime;
    private Date _created;
    private User _createdBy;
    private Date _modified;
    private User _modifiedBy;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public Integer getRecordCount()
    {
        return _recordCount;
    }

    public void setRecordCount(Integer recordCount)
    {
        _recordCount = recordCount;
    }

    public String getTransformId()
    {
        return _transformId;
    }

    public void setTransformId(String transformId)
    {
        _transformId = transformId;
    }

    public int getTransformVersion()
    {
        return _transformVersion;
    }

    public void setTransformVersion(int transformVersion)
    {
        _transformVersion = transformVersion;
    }

    public String getStatus()
    {
        return _status;
    }

    public void setStatus(String status)
    {
        _status = status;
    }

    public Date getStartTime()
    {
        return _startTime;
    }

    public void setStartTime(Date startTime)
    {
        _startTime = startTime;
    }

    public Date getEndTime()
    {
        return _endTime;
    }

    public void setEndTime(Date endTime)
    {
        _endTime = endTime;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public User getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(User createdBy)
    {
        _createdBy = createdBy;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public User getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(User modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }
}
