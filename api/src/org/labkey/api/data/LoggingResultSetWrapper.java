/*
 * Copyright (c) 2014-2016 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.view.UnauthorizedException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by davebradlee on 9/15/14.
 */
public class LoggingResultSetWrapper extends ResultSetWrapper
{
    private final QueryLogging _queryLogging;
    private final Set<Object> _dataLoggingValues = new HashSet<>();


    public LoggingResultSetWrapper(ResultSet rs, @NotNull QueryLogging queryLogging)
    {
        super(rs);
        _queryLogging = queryLogging;
    }


    @Override
    public boolean next() throws SQLException
    {
        boolean isNext = super.next();
        if (isNext)
            updateQueryLogging();
        return isNext;
    }


    @Override
    public void close() throws SQLException
    {
        if (!_queryLogging.isEmpty() && _queryLogging.isShouldAudit())
        {
            SelectQueryAuditEvent selectQueryAuditEvent = _queryLogging.getSelectQueryAuditEvent();
            SelectQueryAuditProvider selectQueryAuditProvider = _queryLogging.getSelectQueryAuditProvider();
            boolean logEmpty = selectQueryAuditEvent.isLogEmptyResults()
                    && (selectQueryAuditProvider == null || selectQueryAuditProvider.isLogEmptyResults());
            if (!_dataLoggingValues.isEmpty() || logEmpty)
            {
                selectQueryAuditEvent.setDataLogging(_queryLogging, _dataLoggingValues);
                AuditLogService.get().addEvent(_queryLogging.getUser(), selectQueryAuditEvent);
            }
        }
        super.close();
    }


    @Override
    public boolean first() throws SQLException
    {
        boolean isValid = super.first();
        if (isValid)
            updateQueryLogging();
        return isValid;
    }


    @Override
    public boolean last() throws SQLException
    {
        boolean isValid = super.last();
        if (isValid)
            updateQueryLogging();
        return isValid;
    }


    @Override
    public boolean absolute(int i) throws SQLException
    {
        boolean isValid = super.absolute(i);
        if (isValid)
            updateQueryLogging();
        return isValid;
    }


    @Override
    public boolean relative(int i) throws SQLException
    {
        boolean isValid = super.relative(i);
        if (isValid)
            updateQueryLogging();
        return isValid;
    }


    @Override
    public boolean previous() throws SQLException
    {
        boolean isValid = super.previous();
        if (isValid)
            updateQueryLogging();
        return isValid;
    }


    protected void updateQueryLogging() throws SQLException
    {
        if (!_queryLogging.isEmpty())
        {
            for (ColumnInfo dataLoggingColumn : _queryLogging.getDataLoggingColumns())
            {
                Object obj = getObject(dataLoggingColumn.getAlias());
                if (null == obj)
                    throw new UnauthorizedException("Unable to read expected data logging column.");
                _dataLoggingValues.add(obj);
            }
        }
    }
}
