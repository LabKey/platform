package org.labkey.api.audit;

import org.labkey.api.data.Container;

public class DetailedAuditTypeEvent extends AuditTypeEvent
{
    private String _oldRecordMap;
    private String _newRecordMap;

    public DetailedAuditTypeEvent()
    {
    }

    public DetailedAuditTypeEvent(String eventType, Container container, String comment)
    {
        super(eventType, container.getId(), comment);
    }

    public DetailedAuditTypeEvent(String eventType, String container, String comment)
    {
        super(eventType, container, comment);
    }

    public String getOldRecordMap()
    {
        return _oldRecordMap;
    }

    public void setOldRecordMap(String oldRecordMap, Container container)
    {
        setOldRecordMap(oldRecordMap);
    }

    public void setOldRecordMap(String oldRecordMap)
    {
        _oldRecordMap = oldRecordMap;
    }

    public String getNewRecordMap()
    {
        return _newRecordMap;
    }

    public void setNewRecordMap(String newRecordMap, Container container)
    {
        setNewRecordMap(newRecordMap);
    }

    public void setNewRecordMap(String newRecordMap)
    {
        _newRecordMap = newRecordMap;
    }
}
