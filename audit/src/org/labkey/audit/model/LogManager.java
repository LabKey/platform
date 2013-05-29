/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.audit.AuditSchema;

import java.sql.SQLException;
import java.util.List;

/**
 * User: Karl Lum
 * Date: Oct 4, 2007
 */
public class LogManager
{
    private static final Logger _log = Logger.getLogger(LogManager.class);
    private static final LogManager _instance = new LogManager();
    private static final int COMMENT_MAX = 500;
    private static final int STRING_KEY_MAX = 1000;

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
        validateFields(event);
        return Table.insert(user, getTinfoAuditLog(), event);
    }

    public AuditLogEvent getEvent(int rowId)
    {
        SimpleFilter filter = new SimpleFilter("RowId", rowId);
        return new TableSelector(getTinfoAuditLog(), filter, null).getObject(AuditLogEvent.class);
    }

    public List<AuditLogEvent> getEvents(Filter filter, Sort sort)
    {
        return new TableSelector(getTinfoAuditLog(), filter, sort).getArrayList(AuditLogEvent.class);
    }

    /**
     * Ensure that the string key fields don't exceed the length specified in the schema
     */
    private void validateFields(AuditLogEvent event)
    {
        event.setKey1(ensureMaxLength(event.getKey1(), STRING_KEY_MAX));
        event.setKey2(ensureMaxLength(event.getKey2(), STRING_KEY_MAX));
        event.setKey3(ensureMaxLength(event.getKey3(), STRING_KEY_MAX));

        event.setComment(ensureMaxLength(event.getComment(), COMMENT_MAX));
    }

    private String ensureMaxLength(String input, int max)
    {
        if (input != null && input.length() > max)
        {
            _log.warn("Audit field input : \n" + input + "\nexceeded the maximum length : " + max);
            return input.substring(0, max-3) + "...";
        }
        return input;
    }
}
