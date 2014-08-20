/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.query.audit;


import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.data.ColumnLogging;
import org.labkey.api.data.Container;
import org.labkey.api.query.FieldKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class SelectQueryAuditEvent extends AuditTypeEvent
{
    private String _loggedColumns;
    private String _identifiedData;
    private String _queryId;

    public SelectQueryAuditEvent()
    {
        super();
        setEventType(SelectQueryAuditProvider.EVENT_NAME);
    }

    public SelectQueryAuditEvent(Container container, String comment)
    {
        super(SelectQueryAuditProvider.EVENT_NAME, container.getId(), comment);
    }

    public SelectQueryAuditEvent(Container container, String comment, Set<ColumnLogging> loggings, Set<FieldKey> idDataFieldKeys, String queryId)
    {
        this(container, comment);

        List<ColumnLogging> sortedLoggings = new ArrayList<>(loggings);
        Collections.sort(sortedLoggings, new Comparator<ColumnLogging>()
        {
            @Override
            public int compare(ColumnLogging o1, ColumnLogging o2)
            {
                int ret = o1.getOriginalTableName().compareToIgnoreCase(o2.getOriginalTableName());
                if (ret < 0) return -1;
                if (ret > 0) return 1;
                return o1.getOriginalColumnFieldKey().compareTo(o2.getOriginalColumnFieldKey());
            }
        });

        StringBuilder loggedColumns = new StringBuilder();
        String sep = "";
        for (ColumnLogging logging : sortedLoggings)
        {
            loggedColumns.append(sep).append(logging.getOriginalTableName() + "." + logging.getOriginalColumnFieldKey());
            sep = ", ";
        }

        List<FieldKey> sortedFieldKeys = new ArrayList(idDataFieldKeys);
        Collections.sort(sortedFieldKeys, new Comparator<FieldKey>()
        {
            @Override
            public int compare(FieldKey o1, FieldKey o2)
            {
                return o1.compareTo(o2);
            }
        });

        StringBuilder dataColumns = new StringBuilder();
        sep = "";
        for (FieldKey fieldKey : sortedFieldKeys)
        {
            dataColumns.append(sep).append(fieldKey.toString());
            sep = ", ";
        }
        _loggedColumns = loggedColumns.toString();
        _identifiedData = dataColumns.toString();
        _queryId = queryId;
    }

    public String getLoggedColumns()
    {
        return _loggedColumns;
    }

    public void setLoggedColumns(String loggedColumns)
    {
        _loggedColumns = loggedColumns;
    }

    public String getIdentifiedData()
    {
        return _identifiedData;
    }

    public void setIdentifiedData(String identifiedData)
    {
        _identifiedData = identifiedData;
    }

    public String getQueryId()
    {
        return _queryId;
    }

    public void setQueryId(String queryId)
    {
        _queryId = queryId;
    }
}
