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
package org.labkey.di.filters;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.util.ConfigurationException;
import org.labkey.di.VariableMap;
import org.labkey.di.data.TransformProperty;
import org.labkey.di.pipeline.TransformConfiguration;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformManager;
import org.labkey.di.pipeline.TransformUtils;
import org.labkey.di.steps.StepMeta;
import org.labkey.etl.xml.FilterType;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.labkey.di.DataIntegrationQuerySchema.Columns;

/**
 * User: matthew
 * Date: 4/22/13
 * Time: 12:26 PM
 */
public class ModifiedSinceFilterStrategy extends FilterStrategyImpl
{
    String _defaultTimestampColumnName = null;
    TableInfo _table;
    ColumnInfo _tsCol;
    ColumnInfo _deletedQueryTsCol;
    boolean _useRowversionForSelect = false;
    boolean _useRowversionForDelete = false;

    private enum FilterTimestamp
    {
        START
            {
                @Override
                public PropertyDescriptor getPropertyDescriptor(boolean useRowversion, boolean deleting)
                {
                    if (useRowversion && !deleting)
                        return TransformProperty.IncrementalStartRowversion.getPropertyDescriptor();
                    if (!useRowversion && !deleting)
                        return TransformProperty.IncrementalStartTimestamp.getPropertyDescriptor();
                    if (useRowversion && deleting)
                        return TransformProperty.DeletedIncrementalStartRowversion.getPropertyDescriptor();
                    if (!useRowversion && deleting)
                        return TransformProperty.DeletedIncrementalStartTimestamp.getPropertyDescriptor();
                    throw new IllegalStateException("Unreachable parameter combination");
                }
            },
        END
            {
                @Override
                public PropertyDescriptor getPropertyDescriptor(boolean useRowversion, boolean deleting)
                {
                    if (useRowversion && !deleting)
                        return TransformProperty.IncrementalEndRowversion.getPropertyDescriptor();
                    if (!useRowversion && !deleting)
                        return TransformProperty.IncrementalEndTimestamp.getPropertyDescriptor();
                    if (useRowversion && deleting)
                        return TransformProperty.DeletedIncrementalEndRowversion.getPropertyDescriptor();
                    if (!useRowversion && deleting)
                        return TransformProperty.DeletedIncrementalEndTimestamp.getPropertyDescriptor();
                    throw new IllegalStateException("Unreachable parameter combination");
                }
            };

        public abstract PropertyDescriptor getPropertyDescriptor(boolean useRowversion, boolean deleting);
    }

    public ModifiedSinceFilterStrategy(TransformJobContext context, StepMeta stepMeta, String defaultTimestampColumnName, DeletedRowsSource deletedRowsSource)
    {
        super(stepMeta, context, deletedRowsSource);
        _defaultTimestampColumnName = defaultTimestampColumnName;
    }

    @Override
    protected void initMainFilter()
    {
        QuerySchema schema = DefaultSchema.get(_context.getUser(), _context.getContainer(), _config.getSourceSchema());
        if (null == schema)
            throw new ConfigurationException("Schema not found: " + _config.getSourceSchema());

        _table = schema.getTable(_config.getSourceQuery());
        if (null == _table)
            throw new ConfigurationException("Table not found: " + _config.getSourceQuery());

        String timestampColumnName = StringUtils.defaultString(_config.getSourceTimestampColumnName(), _defaultTimestampColumnName);
        // For multi-stage ETL's, allow the diModified column in source to be the timestampColumn.
        timestampColumnName = StringUtils.defaultString(timestampColumnName, Columns.TransformModified.getColumnName());
        _tsCol = _table.getColumn(timestampColumnName);
        if (null == _tsCol)
            throw new ConfigurationException("Column not found: " + _config.getSourceQuery() + "." + timestampColumnName);
        if (TransformUtils.isRowversionColumn(_tsCol) || _tsCol.getJdbcType().isInteger())
            _useRowversionForSelect = true;
    }

    @Override
    protected void initDeletedRowsFilter()
    {
        if (null != _deletedRowsSource)
        {
            super.initDeletedRowsFilter();

            String timestampColumnName = StringUtils.defaultString(_deletedRowsSource.getTimestampColumnName(), _tsCol.getColumnName());
            _deletedQueryTsCol = _deletedRowsTinfo.getColumn(timestampColumnName);
            if (null == _deletedQueryTsCol)
                throw new ConfigurationException("Column not found: " + _deletedRowsTinfo.getName() + "." + timestampColumnName);
            if (TransformUtils.isRowversionColumn(_deletedQueryTsCol) || _deletedQueryTsCol.getJdbcType().isInteger())
                _useRowversionForDelete = true;
        }
    }

    @Override
    public boolean hasWork()
    {
        Object incrementalEndTimestamp = initFilterAndGetTimestamps(new SimpleFilter(), false).get(FilterTimestamp.END);

        if (null != _context.getPipelineJob() && null == incrementalEndTimestamp)
            _context.getPipelineJob().getLogger().info("No new rows found in table: " + _table.getName());

        return (null != incrementalEndTimestamp) || hasDeleteWork();
    }

    private boolean hasDeleteWork()
    {
        return null != _deletedRowsSource && null != initFilterAndGetTimestamps(new SimpleFilter(), true).get(FilterTimestamp.END);
    }

    @Override
    public SimpleFilter getFilter(VariableMap variables, boolean deleting)
    {
        init();

        SimpleFilter f = new SimpleFilter();

        Map<FilterTimestamp, Object> timestamps = initFilterAndGetTimestamps(f, deleting);
        Object incrementalStartVal = timestamps.get(FilterTimestamp.START);
        Object incrementalEndVal = timestamps.get(FilterTimestamp.END);

        if (null == incrementalEndVal)
            incrementalEndVal = incrementalStartVal;
        if (null == incrementalEndVal)     // ERROR, no non-null values?
            f.addCondition(new SimpleFilter.FalseClause());
        else
        {
            ColumnInfo tsCol = deleting ? _deletedQueryTsCol : _tsCol;
            f.addCondition(tsCol.getFieldKey(), incrementalEndVal, CompareType.LTE);
        }

        variables.put(FilterTimestamp.START.getPropertyDescriptor(isUseRowversion(deleting), deleting), incrementalStartVal);
        variables.put(FilterTimestamp.END.getPropertyDescriptor(isUseRowversion(deleting), deleting), incrementalEndVal);

        return f;
    }

    private Map<FilterTimestamp, Object> initFilterAndGetTimestamps(SimpleFilter f, boolean deleting)
    {
        init();
        TableInfo table;
        ColumnInfo tsCol;
        if (deleting)
        {
            table = _deletedRowsTinfo;
            tsCol = _deletedQueryTsCol;
        }
        else
        {
            table = _table;
            tsCol = _tsCol;
        }
        if (null == table || null == tsCol) // Should never happen, but just to be safe
            throw new IllegalStateException("NULL value for table or timestamp column name initializing filter.");

        boolean useOverrideWindow = null != _context.getIncrementalWindow();
        Object incrementalStartTimestamp = useOverrideWindow ? _context.getIncrementalWindow().first : getLastSuccessfulIncrementalEndTimestampJson(deleting);
        if (null != incrementalStartTimestamp)
            f.addCondition(tsCol.getFieldKey(), incrementalStartTimestamp, CompareType.GT);

        SimpleFilter findMaxTsFilter = new SimpleFilter().addAllClauses(f);
        if (useOverrideWindow && null != _context.getIncrementalWindow().second)
        {
            findMaxTsFilter.addCondition(tsCol.getFieldKey(), _context.getIncrementalWindow().second, CompareType.LTE);
        }
        // Consider the timestamps of rows in *all* containers in scope by a specified filter
        if (null != _config.getSourceContainerFilter() && table.supportsContainerFilter())
        {
            ((ContainerFilterable)table).setContainerFilter(ContainerFilter.getContainerFilterByName(_config.getSourceContainerFilter(), _context.getUser()));
        }

        Aggregate max = new Aggregate(tsCol, Aggregate.BaseType.MAX);

        TableSelector ts = new TableSelector(table, Collections.singleton(tsCol), findMaxTsFilter, null);
        Map<String, List<Aggregate.Result>> results;

        // Issue 24301, from exception 21337. A misconfigured timestamp column can fail in lots of ways.
        // Perhaps getAggregates() itself could be hardened, but we'd still want it to throw something so the caller could deal with the error appropriately.
        try
        {
            results = ts.getAggregates(Arrays.asList(max));
        }
        catch (DataIntegrityViolationException ex)
        {
            throw new ConfigurationException("Timestamp column '"+ _table.getName() + "." + tsCol.getColumnName()+"' contains value not castable to a legal type.", ex);
        }
        catch (Exception ex)
        {
            throw new ConfigurationException("Problem with configured timestamp column '"+ _table.getName() + "." + tsCol.getColumnName(), ex);
        }

        List<Aggregate.Result> list = results.get(tsCol.getName());

        // Diagnostic for 20659, from exception 17683. The aggregates resultset doesn't include the tsCol name.
        if (list == null)
        {
            throw new ConfigurationException(makeBadTsColMessage("Timestamp column '" + tsCol.getName() + "' not found in aggregate results for table '" + _table.getName(), results));
        }
        Aggregate.Result maxResult = list.get(0);

        Map<FilterTimestamp, Object> timestamps = new HashMap<>();
        timestamps.put(FilterTimestamp.START, incrementalStartTimestamp);
        Object incrementalEndTimestamp = maxResult.getValue();
        if (incrementalEndTimestamp == null || incrementalEndTimestamp instanceof Date || incrementalEndTimestamp instanceof Number)
            timestamps.put(FilterTimestamp.END, incrementalEndTimestamp);
        else if (isUseRowversion(deleting) && incrementalEndTimestamp instanceof byte[])
            timestamps.put(FilterTimestamp.END, ByteBuffer.wrap((byte[]) incrementalEndTimestamp).getLong()); // a SQL Server timestamp column
        else if (incrementalEndTimestamp instanceof String)
        {
            try
            {
                timestamps.put(FilterTimestamp.END, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse((String) incrementalEndTimestamp));
            }
            catch (ParseException e)
            {
                throw new ConfigurationException(makeBadTsColMessage("Timestamp column '"+ tsCol.getColumnName()+"' contains value not castable to a legal type: " + incrementalEndTimestamp.toString(), results));
            }
        }
        else
            throw new ConfigurationException(makeBadTsColMessage("Timestamp column '"+ tsCol.getColumnName()+"' contains value not castable to a legal type: " + incrementalEndTimestamp.toString(), results));
        return timestamps;
    }

    @NotNull
    private String makeBadTsColMessage(String specificMsg, Map<String, List<Aggregate.Result>> results)
    {
        StringBuilder sb = new StringBuilder(specificMsg + "'\n");
        sb.append("Legal types are date, time, datetime, timestamp, rowversion, and integer.\nText fields will pass the datatype check but immediately fail on the cast attempt.\nAvailable columns:\n");
        for (String column : results.keySet())
            sb.append(column).append("\n");
        return sb.toString();
    }

    private Object getLastSuccessfulIncrementalEndTimestampJson(boolean deleted)
    {
        TransformConfiguration cfg = TransformManager.get().getTransformConfiguration(_context.getContainer(), _context.getJobDescriptor());
        JSONObject state = cfg.getJsonState();
        if (null == state || state.isEmpty())
            return null;
        JSONObject steps = _getObject(state, "steps");
        JSONObject step = _getObject(steps, _config.getId());
        Object o = _get(step, FilterTimestamp.END.getPropertyDescriptor(isUseRowversion(deleted), deleted).getName());

        if (null == o || (o instanceof String && StringUtils.isEmpty((String)o)))
            return null;
        if (o instanceof Number)
            return o;
        try
        {
            if (o instanceof String)
                return Timestamp.valueOf((String) o);
        }
        catch (Exception x)
        {
            _context.getPipelineJob().getLogger().warn("Exception converting timestamp: " + o + "\n", x);
        }
        return JdbcType.TIMESTAMP.convert(o);
    }


    private static JSONObject _getObject(JSONObject json, String property)
    {
        return null!=json && json.has(property) ? json.getJSONObject(property) : null;
    }

    private static Object _get(JSONObject json, String property)
    {
        return null!=json && json.has(property) ? json.get(property) : null;
    }

    private boolean isUseRowversion(boolean deleting)
    {
        return deleting ? _useRowversionForDelete : _useRowversionForSelect;
    }

    public static class Factory extends FilterStrategyFactoryImpl
    {
        private final String _tsColumnName;

        public Factory(@Nullable FilterType ft)
        {
            super(ft);
            _tsColumnName = null == ft ? null : ft.getTimestampColumnName();
        }

        @Override
        public FilterStrategy getFilterStrategy(TransformJobContext context, StepMeta stepMeta)
        {
            return new ModifiedSinceFilterStrategy(context, stepMeta, _tsColumnName, _deletedRowsSource);
        }

        @Override
        public boolean checkStepsSeparately()
        {
            return true;
        }

        @Override
        public Type getType()
        {
            return Type.ModifiedSince;
        }
    }

    @Override
    public String getLogMessage(@Nullable String filterValue)
    {
        return super.getLogMessage(filterValue) + "\n" + "The max filter value is the max value in the source time column when the job started.";
    }
}
