/*
 * Copyright (c) 2007-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.audit.model;

import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
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

    public AuditLogEvent[] getEvents(Filter filter) throws SQLException
    {
        return Table.select(getTinfoAuditLog(), Table.ALL_COLUMNS, filter, null, AuditLogEvent.class);
    }
}
