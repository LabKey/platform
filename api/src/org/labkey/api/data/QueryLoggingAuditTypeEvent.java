package org.labkey.api.data;

import org.labkey.api.audit.AuditTypeEvent;

/**
 * User: tgaluhn
 * Date: 10/8/2015
 *
 * Basic audit type event to log the sql string of db queries
 */
public class QueryLoggingAuditTypeEvent extends AuditTypeEvent
{
    private String _sql;

    public QueryLoggingAuditTypeEvent()
    {
        super();
        setEventType(QueryLoggingAuditTypeProvider.EVENT_NAME);
    }

    public String getSQL()
    {
        return _sql;
    }

    public void setSQL(String sql)
    {
        _sql = sql;
    }
}
