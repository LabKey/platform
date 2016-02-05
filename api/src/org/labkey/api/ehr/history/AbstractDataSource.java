/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 2/17/13
 * Time: 6:51 PM
 */
abstract public class AbstractDataSource implements HistoryDataSource
{
    private String _schema;
    private String _query;
    private String _categoryText;
    private String _primaryGroup;
    private String _name;
    private boolean _showTime = false;
    protected String _subjectIdField = "Id";
    protected static final Logger _log = Logger.getLogger(HistoryDataSource.class);

    public AbstractDataSource(String schema, String query)
    {
        this(schema, query, query);
    }

    public AbstractDataSource(String schema, String query, String categoryText)
    {
        this(schema, query, categoryText, categoryText);
    }

    public AbstractDataSource(String schema, String query, String categoryText, String primaryGroup)
    {
        _schema = schema;
        _query = query;
        _name = categoryText;
        _categoryText = categoryText;
        _primaryGroup = primaryGroup;
    }

    public String getName()
    {
        return _name;
    }


    protected TableInfo getTableInfo(Container c, User u)
    {
        UserSchema us = QueryService.get().getUserSchema(u, c, _schema);
        if (us == null)
            return null;

        return us.getTable(_query);
    }

    protected void setShowTime(boolean showTime)
    {
        _showTime = showTime;
    }

    @NotNull
    public List<HistoryRow> getRows(Container c, User u, String subjectId, String caseId, boolean redacted)
    {
        String caseIdField = "caseId";
        TableInfo ti = getTableInfo(c, u);
        if (ti == null)
            return  Collections.emptyList();

        if (ti.getColumn(caseIdField) == null)
            return Collections.emptyList();

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString(caseIdField), caseId, CompareType.EQUAL);
        filter.addCondition(FieldKey.fromString("Id"), subjectId, CompareType.EQUAL);
        return getRows(c, u, filter, redacted);
    }

    @NotNull
    public List<HistoryRow> getRows(Container c, User u, final String subjectId, Date minDate, Date maxDate, boolean redacted)
    {
        SimpleFilter filter = getFilter(subjectId, minDate, maxDate);
        return getRows(c, u, filter, redacted);
    }

    @NotNull
    protected List<HistoryRow> getRows(Container c, User u, SimpleFilter filter, final boolean redacted)
    {
        Date start = new Date();
        final TableInfo ti = getTableInfo(c, u);
        if (ti == null)
            return  Collections.emptyList();

        final Collection<ColumnInfo> cols = getColumns(ti);

        //always show only public when redacted
        if (redacted && ti.getColumn("qcstate") != null)
            filter.addCondition(FieldKey.fromString("QCState/publicdata"), true, CompareType.EQUAL);

        TableSelector ts = new TableSelector(ti, cols, filter, getSort(ti));
        ts.setForDisplay(true);

        List<HistoryRow> rows = processRows(c, ts, redacted, cols);

        Long duration = ((new Date()).getTime() - start.getTime()) / 1000;
        if (duration > 5)
        {
            String msg = "Loaded history on table " + _query + " in " + duration + " seconds";
            _log.error(msg);
            LDKService.get().logPerfMetric(c, u, "EHRDataSource", msg, duration.doubleValue());
        }

        return rows;
    }

    @Nullable
    protected Sort getSort(TableInfo ti)
    {
        //if this table uses formSort, remember when showing results
        if (ti.getColumn("formSort") != null && ti.getColumn("Id") != null && ti.getColumn("date") != null)
        {
            return new Sort("Id,date,formSort");
        }

        return null;
    }

    protected List<HistoryRow> processRows(Container c, TableSelector ts, final boolean redacted, final Collection<ColumnInfo> cols)
    {
        final List<HistoryRow> rows = new ArrayList<>();
        ts.forEach(rs -> {
            Results results = new ResultsImpl(rs, cols);
            Date date = results.getTimestamp(getDateField());
            String categoryText = getCategoryText(results);
            String categoryColor = getCategoryColor(results);
            String categoryGroup = getPrimaryGroup(results);
            String taskId = results.hasColumn(FieldKey.fromString("taskid")) ? results.getString(FieldKey.fromString("taskid")) : null;
            String objectId = results.hasColumn(FieldKey.fromString("objectid")) ? results.getString(FieldKey.fromString("objectid")) : null;
            Integer taskRowId = results.hasColumn(FieldKey.fromString("taskid/rowid")) ? results.getInt(FieldKey.fromString("taskid/rowid")) : null;
            String formType = results.hasColumn(FieldKey.fromString("taskid/formtype")) ? results.getString(FieldKey.fromString("taskid/formtype")) : null;

            String html = getHtml(c, results, redacted);
            String subjectId = results.getString(FieldKey.fromString(_subjectIdField));
            if (!StringUtils.isEmpty(html))
            {
                HistoryRow row = createHistoryRow(results, categoryText, categoryGroup, categoryColor, subjectId, date, html, taskId, taskRowId, formType, objectId);
                if (row != null)
                    rows.add(row);
            }
        });

        return rows;
    }

    protected HistoryRow createHistoryRow(Results results, String categoryText, String categoryGroup, String categoryColor, String subjectId, Date date, String html, String taskId, Integer taskRowId, String formType, String objectId) throws SQLException
    {
        String qcStateLabel = results.hasColumn(FieldKey.fromString("qcstate/Label")) ? results.getString(FieldKey.fromString("qcstate/Label")) : null;
        Boolean publicData = results.hasColumn(FieldKey.fromString("qcstate/PublicData")) ? results.getBoolean(FieldKey.fromString("qcstate/PublicData")) : true;
        if (!results.hasColumn(FieldKey.fromString("qcstate/PublicData")))
        {
            _log.info("DataSource does not contain QCState: " + getName());
        }

        HistoryRowImpl ret = new HistoryRowImpl(this, categoryText, categoryGroup, categoryColor, subjectId, date, html, qcStateLabel, publicData, taskId, taskRowId, formType, objectId);
        if (_showTime && ret != null)
            ret.setShowTime(_showTime);

        return ret;
    }

    protected String getCategoryText(Results rs) throws SQLException
    {
        return _categoryText;
    }

    protected String getCategoryColor(Results rs) throws SQLException
    {
        return null;
    }

    public Set<String> getAllowableCategoryGroups(Container c, User u)
    {
        return Collections.singleton(_primaryGroup);
    }

    protected String getPrimaryGroup(Results rs) throws SQLException
    {
        return _primaryGroup;
    }

    private Collection<FieldKey> getAlwaysPresentFieldKeys(TableInfo ti)
    {
        Set<FieldKey> cols = new HashSet<>();

        cols.add(FieldKey.fromString("qcstate"));
        cols.add(FieldKey.fromString("qcstate/Label"));
        cols.add(FieldKey.fromString("qcstate/PublicData"));
        cols.add(FieldKey.fromString("taskid"));
        cols.add(FieldKey.fromString("taskid/rowid"));
        cols.add(FieldKey.fromString("taskid/formtype"));
        cols.add(FieldKey.fromString("requestid"));
        cols.add(FieldKey.fromString("objectid"));

        return cols;
    }

    private Collection<ColumnInfo> getColumns(TableInfo ti)
    {
        List<FieldKey> columns = new ArrayList<>();
        if (getColumnNames() != null)
        {
            for (String colName : getColumnNames())
            {
                columns.add(FieldKey.fromString(colName));
            }
        }
        else
        {
            columns.addAll(ti.getDefaultVisibleColumns());
        }

        columns.addAll(getAlwaysPresentFieldKeys(ti));

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

    protected SimpleFilter getFilter(String subjectId, Date minDate, Date maxDate)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString(_subjectIdField), subjectId);

        if (minDate != null)
            filter.addCondition(FieldKey.fromString(getDateField()), minDate, CompareType.DATE_GTE);

        if (maxDate != null)
            filter.addCondition(FieldKey.fromString(getDateField()), maxDate, CompareType.DATE_LTE);

        return filter;
    }

    protected String getDateField()
    {
        return "date";
    }

    protected Set<String> getColumnNames()
    {
        return null;
    }

    protected String snomedToString(Results rs, FieldKey codeField, FieldKey meaningField) throws SQLException
    {
        StringBuilder sb = new StringBuilder();
        if (rs.hasColumn(meaningField))
        {
            sb.append(rs.getString(meaningField));
            if (rs.hasColumn(codeField) && !StringUtils.isEmpty(rs.getString(codeField)))
            {
                sb.append(" (").append(rs.getString(codeField)).append(")");
            }
            sb.append("\n");
        }
        else
        {
            if (rs.hasColumn(codeField) && !StringUtils.isEmpty(rs.getString(codeField)))
            {
                sb.append(rs.getString(codeField)).append("\n");
            }
        }

        return sb.toString();
    }

    protected String safeAppend(Results rs, String label, String field) throws SQLException
    {
        return safeAppend(rs, label, field, null);
    }

    protected String safeAppend(Results rs, String label, String field, String suffix) throws SQLException
    {
        FieldKey fk = FieldKey.fromString(field);
        if (rs.hasColumn(fk) && rs.getObject(fk) != null)
        {
            return (label == null ? "" : label + ": ") + rs.getString(fk) + (suffix == null ? "" : suffix) + "\n";
        }
        return "";
    }

    public boolean isAvailable(Container c, User u)
    {
        return c.getActiveModules(u).contains(ModuleLoader.getInstance().getModule("ehr"));
    }

    abstract protected String getHtml(Container c, Results rs, boolean redacted) throws SQLException;
}
