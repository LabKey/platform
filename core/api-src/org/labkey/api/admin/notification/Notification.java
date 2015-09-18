package org.labkey.api.admin.notification;

import org.labkey.api.util.GUID;

/**
 * User: cnathe
 * Date: 9/14/2015
 */
public class Notification
{
    private int _rowId;
    private GUID _container;
    private int _userId;
    private String _objectId;
    private String _type;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public int getUserId()
    {
        return _userId;
    }

    public void setUserId(int userId)
    {
        _userId = userId;
    }

    public String getObjectId()
    {
        return _objectId;
    }

    public void setObjectId(String objectId)
    {
        _objectId = objectId;
    }

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }

    public GUID getContainer()
    {
        return _container;
    }

    public void setContainer(GUID container)
    {
        _container = container;
    }
}
