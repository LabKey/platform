/*
 * Copyright (c) 2014-2018 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.util.Pair;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        try
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
        }
        finally
        {
            super.close();
        }
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


    List<Pair<ColumnInfo,Integer>> dataLoggingColumns = null;

    protected void updateQueryLogging() throws SQLException
    {
        // For now, only check for expected data logging columns if auditing is turned on; clients get confused when
        // this message is thrown with compliance logging turned off. See #44752.
        // In the future, consider always performing this check under an assert or dev-mode switch to point out cases we
        // need to improve.
        if (!_queryLogging.isEmpty() && _queryLogging.isShouldAudit())
        {
            if (null == dataLoggingColumns) // FIRST
            {
                dataLoggingColumns = _queryLogging.getDataLoggingColumns().stream().map(column ->
                {
                    try
                    {
                        int index = findColumn(column.getAlias().toLowerCase());
                        return new Pair<>(column,index);
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeSQLException(e);
                    }
                }).toList();
            }

            List<ColumnInfo> missingColumns = null; // = new ArrayList<>();
            for (Pair<ColumnInfo, Integer> dataLoggingColumn : dataLoggingColumns)
            {
                int index = dataLoggingColumn.second;
                Object obj = getObject(index);
                if (null != obj)
                    _dataLoggingValues.add(obj);
                else
                {
                    if (null == missingColumns)
                        missingColumns = new ArrayList<>();
                    missingColumns.add(dataLoggingColumn.first);
                }
            }

            if (null != missingColumns)
            {
                throw new IllegalStateException("Unable to read expected data logging column(s) for " +
                        StringUtils.join(missingColumns.stream().map(c -> "\"" + c.getFieldKey().toString() + "\"").collect(Collectors.toList()), ';') +
                        " with alias(es) " +
                        StringUtils.join(missingColumns.stream().map(c -> "\"" + c.getAlias() + "\"").collect(Collectors.toList()), ';')
                );
            }
        }
    }
}
