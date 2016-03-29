/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
package org.labkey.api.ehr.history;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 3/6/13
 * Time: 11:14 AM
 */
public class DefaultLabworkType implements LabworkType
{
    protected String _lsidField = "lsid";
    protected String _idField = "Id";
    protected String _runIdField = "runId";
    protected String _dateField = "date";
    protected String _performedByField = "performedby";
    protected String _remarkField = "remark";

    private String _name;
    private String _schemaName;
    private String _queryName;

    protected String _testIdField = "testid";
    protected String _resultField = "result";
    protected String _unitsField = "units";
    protected String _qualResultField = "qualresult";

    protected String _normalRangeField = null;
    protected String _normalRangeStatusField = null;

    private static final Logger _log = Logger.getLogger(DefaultLabworkType.class);
    @NotNull
    private final Module _declaringModule;

        public DefaultLabworkType(String name, String schemaName, String queryName, Module declaringModule)
    {
        _name = name;
        _schemaName = schemaName;
        _queryName = queryName;
        _declaringModule = declaringModule;
    }

    protected TableInfo getTableInfo(Container c, User u)
    {
        UserSchema us = QueryService.get().getUserSchema(u, c, _schemaName);
        if (us == null)
            return null;

        TableInfo ti = us.getTable(_queryName);
        if (ti == null)
            return null;

        if (ti.getColumn(FieldKey.fromString("runId")) == null)
        {
            _log.warn("Attempting to use a LabworkType without a runId field");
            return null;
        }

        return ti;
    }

    public String getName()
    {
        return _name;
    }

    @Override
    public boolean isEnabled(Container c)
    {
        return c.getActiveModules().contains(_declaringModule);
    }

    public List<String> getResults(Container c, User u, String runId, boolean redacted)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString(_runIdField), runId, CompareType.EQUAL);
        Map<String, List<String>> rows = getResults(c, u, filter, redacted);
        return rows == null ? null : rows.get(runId);
    }

    public Map<String, List<String>> getResults(Container c, User u, List<String> runIds, boolean redacted)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString(_runIdField), runIds, CompareType.IN);
        return getResults(c, u, filter, redacted);
    }

    public Map<String, List<String>> getResults(Container c, User u, String id, @Nullable Date minDate, @Nullable Date maxDate, boolean redacted)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString(_idField), id, CompareType.EQUAL);

        if (minDate != null)
            filter.addCondition(FieldKey.fromString(_dateField), minDate, CompareType.DATE_GTE);

        if (maxDate != null)
            filter.addCondition(FieldKey.fromString(_dateField), maxDate, CompareType.DATE_LTE);

        return getResults(c, u, filter, redacted);
    }

    protected Map<String, List<String>> getResults(Container c, User u, SimpleFilter filter, boolean redacted)
    {
        final TableInfo ti = getTableInfo(c, u);
        if (ti == null)
        {
            return new HashMap<>();
        }

        if (ti.getColumn("qcstate") != null)
            filter.addCondition(FieldKey.fromString("QCState/publicdata"), true, CompareType.EQUAL);

        assert ti.getColumn(_runIdField) != null : "Unable to find runId column for table: " + _queryName;
        final Collection<ColumnInfo> cols = getColumns(ti);
        TableSelector ts = new TableSelector(ti, cols, filter, null);

        Map<String, List<String>> rows = getRows(ts, cols, redacted);

        Map<String, List<String>> formattedRows = new CaseInsensitiveHashMap<>();
        for (String runId : rows.keySet())
        {
            List<String> results = rows.get(runId);
            String table = getResultTable(results);

            List<String> newRows = formattedRows.get(runId);
            if (newRows == null)
                newRows = new ArrayList<>();

            newRows.add(table);

            formattedRows.put(runId, newRows);
        }

        return formattedRows;
    }

    protected Map<String, List<String>> getRows(TableSelector ts, final Collection<ColumnInfo> cols, final boolean redacted)
    {
        final Map<String, List<String>> rows = new CaseInsensitiveHashMap<>();
        ts.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet object) throws SQLException
            {
                Results rs = new ResultsImpl(object, cols);
                String runId = rs.getString(FieldKey.fromString("runId"));

                List<String> list = rows.get(runId);
                if (list == null)
                    list = new ArrayList<>();

                String line = getLine(rs, redacted);
                if (line != null)
                    list.add(line);

                rows.put(runId, list);
            }
        });

        return rows;
    }

    protected Set<String> getColumnNames()
    {
        Set<String> fields = PageFlowUtil.set(_lsidField, _idField, _dateField, _runIdField, _testIdField, _resultField, _unitsField, _qualResultField, _remarkField, _performedByField);

        if (_normalRangeField != null)
            fields.add(_normalRangeField);

        if (_normalRangeStatusField != null)
            fields.add(_normalRangeStatusField);

        return fields;
    }

    protected List<String> sortResults(List<String> results)
    {
        return results;
    }

    protected String getLine(Results rs, boolean redacted) throws SQLException
    {
        StringBuilder sb = new StringBuilder();
        String testId = getTestId(rs);
        Double result = _resultField == null ?  null : rs.getDouble(FieldKey.fromString(_resultField));
        String units = _unitsField == null ?  null : rs.getString(FieldKey.fromString(_unitsField));
        String qualResult = _qualResultField == null ?  null : rs.getString(FieldKey.fromString(_qualResultField));

        if (result != null || qualResult != null)
        {
            sb.append("<td style='padding: 2px;'>").append(testId).append(": ").append("</td>");
            sb.append("<td style='padding: 2px;'>");

            boolean unitsAppended = false;
            if (result != null)
            {
                sb.append(result);
                if (units != null)
                {
                    sb.append(" ").append(units);
                    unitsAppended = true;
                }
            }

            if (qualResult != null)
            {
                if (result != null)
                    sb.append(", ");

                sb.append(qualResult);

                if (units != null && !unitsAppended)
                    sb.append(" ").append(units);
            }

            sb.append("</td>");

            //append normals
            String normalRange = _normalRangeField == null ?  null : rs.getString(FieldKey.fromString(_normalRangeField));
            String status = _normalRangeStatusField == null ?  null : rs.getString(FieldKey.fromString(_normalRangeStatusField));
            if (normalRange != null)
            {
                if (status != null)
                {
                    String color = "green";
                    if (status.equals("High"))
                        color = "#E3170D";
                    else if (status.equals("Low"))
                        color = "#FBEC5D";

                    sb.append("<td style='padding: 2px;background-color: " + color + ";'>&nbsp;" + status + "&nbsp;</td>");
                }

                sb.append("<td style='padding: 2px;'>");
                sb.append(" (").append(normalRange).append(")");
                sb.append("</td>");
            }
        }

        return sb.toString();
    }

    private Collection<ColumnInfo> getColumns(TableInfo ti)
    {
        if (getColumnNames() == null)
        {
            Set<ColumnInfo> cols = new HashSet<>();
            for (FieldKey fk : ti.getDefaultVisibleColumns())
            {
                cols.add(ti.getColumn(fk));
            }
            return cols;
        }

        List<FieldKey> columns = new ArrayList<>();
        for (String colName : getColumnNames())
        {
            if (colName == null)
                continue;

            columns.add(FieldKey.fromString(colName));
        }

        QueryService qs = QueryService.get();
        Map<FieldKey, ColumnInfo> map = qs.getColumns(ti, columns);
        Set<FieldKey> fieldKeys = new LinkedHashSet<>();

        for (ColumnInfo col : map.values())
        {
            col.getRenderer().addQueryFieldKeys(fieldKeys);
        }

        map = qs.getColumns(ti, fieldKeys);

        return map.values();
    }

    protected String getTestId(Results rs) throws SQLException
    {
        if (_testIdField == null)
            return null;

        FieldKey test = FieldKey.fromString(_testIdField);
        String testId = rs.getString(test);
        if (testId == null && test.getParent() != null)
        {
            testId = rs.getString(test.getParent());
        }

        return testId;
    }

    protected String getResultTable(List<String> results)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<table>");
        sb.append("<tr><td>").append(StringUtils.join(results, "</td></tr><tr><td>")).append("</td></tr>");
        sb.append("</table>");
        return sb.toString();
    }
}
