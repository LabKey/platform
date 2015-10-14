/*
 * Copyright (c) 2015 LabKey Corporation
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
