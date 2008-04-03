package org.labkey.audit.model;

import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.security.User;
import org.labkey.api.reports.report.ReportDB;
import org.labkey.audit.AuditSchema;

import java.sql.SQLException;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 4, 2007
 */
public class LogManager
{
    static private final LogManager _instance = new LogManager();

    private LogManager(){}
    static public LogManager get()
    {
        return _instance;
    }

    public DbSchema getSchema()
    {
        return AuditSchema.getInstance().getSchema();
    }

    public TableInfo getTinfoAuditLog()
    {
        return getSchema().getTable("AuditLog");
    }

    public AuditLogEvent insertEvent(User user, AuditLogEvent event) throws SQLException
    {
        return Table.insert(user, getTinfoAuditLog(), event);
    }

    public AuditLogEvent getEvent(int rowId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("RowId", rowId);
        return Table.selectObject(getTinfoAuditLog(), filter, null, AuditLogEvent.class);
    }
}
