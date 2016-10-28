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
import org.json.JSONObject;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.util.ConfigurationException;
import org.labkey.di.VariableMap;
import org.labkey.di.data.TransformProperty;
import org.labkey.di.pipeline.TransformConfiguration;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformManager;
import org.labkey.di.steps.StepMeta;
import org.labkey.etl.xml.FilterType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.labkey.di.DataIntegrationQuerySchema.Columns;

/**
 * User: matthew
 * Date: 5/13/13
 * Time: 1:12 PM
 *
 * Run Filters rely on a separate run table, specified in the xml. This is a list of runs (synonym with batch, batchId, transferId, etc),
 * which a further upstream process has used to stage records in the source defined for the LabKey etl. The source records must have a
 * corresponding run (or batchId, etc) column indicating they were staged from that particular run.
 *
 * This filter class will took to the run table to find the next set of batches it hasn't processed, and then select the rows from
 * source which were staged by those batch ids.
 */
public class RunFilterStrategy extends FilterStrategyImpl
{
    private SchemaKey _runTableSchema;
    private String _runTableName;
    private TableInfo _runsTable;
    private String _pkColumnName = null;
    private ColumnInfo _runPkCol;

    private TableInfo _sourceTable;
    private String _fkDefaultColumnName = null;
    private ColumnInfo _sourceFkCol;
    private ColumnInfo _deletedFkCol;
    private boolean _useIncrementalWindow;


    public RunFilterStrategy(TransformJobContext context, StepMeta stepMeta, SchemaKey runTableSchema, String runTableName, String pkRunColumnName, String fkRunColumnName, DeletedRowsSource deletedRowsSource)
    {
        super(stepMeta, context, deletedRowsSource);
        _runTableSchema = runTableSchema;
        _runTableName = runTableName;
        _pkColumnName = pkRunColumnName;
        _fkDefaultColumnName = fkRunColumnName;
        _useIncrementalWindow = context.getIncrementalWindow() != null;
    }


    public RunFilterStrategy(TransformJobContext context, StepMeta stepMeta, Factory f)
    {
        this(context, stepMeta, f._runTableSchema, f._runTableName, f._pkColumnName, f._fkColumnName, f._deletedRowsSource);
    }

    @Override
    protected void initMainFilter()
    {
        SchemaKey runSchemaKey = null!=_runTableSchema ? _runTableSchema : _config.getSourceSchema();
        QuerySchema runSchema = DefaultSchema.get(_context.getUser(), _context.getContainer(), runSchemaKey);
        if (null == runSchema)
            throw new ConfigurationException("Schema not found: " + runSchemaKey);

        _runsTable = runSchema.getTable(_runTableName);
        if (null == _runsTable)
            throw new ConfigurationException("Table not found: " + _runTableName);
        _runPkCol = _runsTable.getColumn(_pkColumnName);
        if (null == _runPkCol)
            throw new ConfigurationException("Column not found: " + _runTableName + "." + _pkColumnName);


        if (null != _config && _config.isUseSource())
        {
            QuerySchema sourceSchema = DefaultSchema.get(_context.getUser(), _context.getContainer(), _config.getSourceSchema());
            if (null == sourceSchema)
                throw new ConfigurationException("Source schema not found: " + _config.getSourceSchema().toString());

            _sourceTable = sourceSchema.getTable(_config.getSourceQuery());
            if (null == _sourceTable)
                throw new ConfigurationException("Table not found: " + _config.getSourceQuery());

            String runColumnName = StringUtils.defaultString(_config.getSourceRunColumnName(), _fkDefaultColumnName);
            runColumnName = StringUtils.defaultString(runColumnName, Columns.TransformRunId.getColumnName());
            _sourceFkCol = _sourceTable.getColumn(runColumnName);
            if (null == _sourceFkCol)
                throw new ConfigurationException("Column not found: " + _config.getSourceQuery() + "." + runColumnName);
        }
    }

    @Override
    protected void initDeletedRowsFilter()
    {
        if (null != _deletedRowsSource)
        {
            super.initDeletedRowsFilter();

            String runColumnName = StringUtils.defaultString(_deletedRowsSource.getRunColumnName(), _sourceFkCol.getColumnName());
            runColumnName =  StringUtils.defaultString(runColumnName, _fkDefaultColumnName);
            runColumnName =  StringUtils.defaultString(runColumnName, Columns.TransformRunId.getColumnName());
            _deletedFkCol = _deletedRowsTinfo.getColumn(runColumnName);
            if (null == _deletedFkCol)
                throw new ConfigurationException("Column not found: " + _deletedRowsTinfo.getName() + "." + runColumnName);
        }
    }

    @Override
    public boolean hasWork()
    {
        init();

        return null != findNextRunId(false) || hasDeleteWork();
    }

    private boolean hasDeleteWork()
    {
        return (null != _deletedRowsSource && null != findNextRunId(true));
    }


    private Integer findNextRunId(boolean deleting)
    {
        SimpleFilter f = new SimpleFilter();
        Integer lastRunId = _useIncrementalWindow ? (Integer) _context.getIncrementalWindow().first : getLastSuccessfulRunIdJSON(deleting);
        if (null != lastRunId)
            f.addCondition(_runPkCol.getFieldKey(), lastRunId, CompareType.GT);

        TableSelector ts = new TableSelector(_runsTable, Collections.singleton(_runPkCol), f, null);
        Aggregate min = new Aggregate(_runPkCol, Aggregate.BaseType.MIN);
        Map<String, List<Aggregate.Result>> results = ts.getAggregates(Collections.singletonList(min));
        List<Aggregate.Result> list = results.get(_runPkCol.getName());
        Aggregate.Result minResult = list.get(0);
        Integer nextRunId = null;
        if (null != minResult.getValue())
        {
            nextRunId = ((Number) minResult.getValue()).intValue();
            if (_useIncrementalWindow && nextRunId > (Integer) _context.getIncrementalWindow().second)
            {
                nextRunId = (Integer) _context.getIncrementalWindow().second;
            }
        }
        return nextRunId;
    }


    @Override
    public SimpleFilter getFilter(VariableMap variables, boolean deleting)
    {
        init();

        Integer nextRunId;
        Object v = variables.get(getRunPropertyDescriptor(deleting).getName());
        // The incremental run id is saved at global scope. We shouldn't increment it for each step in the ETL
        Boolean ranStep1 = (Boolean)variables.get(TransformProperty.RanStep1);
        if (v instanceof Integer && ranStep1 && !deleting)
        {
            nextRunId = (Integer)v;
        }
        else
        {
            nextRunId = findNextRunId(deleting);
            variables.put(getRunPropertyDescriptor(deleting), nextRunId, VariableMap.Scope.global);
            if (!deleting)
                variables.put(TransformProperty.RanStep1, true, VariableMap.Scope.global);
        }

        if (null != nextRunId)
        {
            FieldKey sourceField;
            if (deleting)
                sourceField = _deletedFkCol.getFieldKey();
            else sourceField = _config.isUseSource() ? _sourceFkCol.getFieldKey() : FieldKey.fromString("filterRunId");
            return new SimpleFilter(sourceField, nextRunId, CompareType.EQUAL);
        }
        else
            return new SimpleFilter(new SimpleFilter.FalseClause());
    }

    private PropertyDescriptor getRunPropertyDescriptor(boolean deleting)
    {
        if (deleting)
            return TransformProperty.DeletedIncrementalRunId.getPropertyDescriptor();
        else return TransformProperty.IncrementalRunId.getPropertyDescriptor();
    }

    Integer getLastSuccessfulRunIdJSON(boolean deleting)
    {
        TransformConfiguration cfg = TransformManager.get().getTransformConfiguration(_context.getContainer(), _context.getJobDescriptor());
        JSONObject state = cfg.getJsonState();
        String propertyName = getRunPropertyDescriptor(deleting).getName();
        Object o = state.has(propertyName) ? state.get(propertyName) : null;
        if (null == o || (o instanceof String && StringUtils.isEmpty((String)o)))
            return null;
        return (Integer)JdbcType.INTEGER.convert(o);
    }


    public static class Factory extends FilterStrategyFactoryImpl
    {
        private final SchemaKey _runTableSchema;
        private final String _runTableName;
        private final String _pkColumnName;
        private final String _fkColumnName;

        public Factory(FilterType ft)
        {
            super(ft);
            _runTableSchema = null != ft.getRunTableSchema() ? SchemaKey.decode(ft.getRunTableSchema()) : null;
            _runTableName = ft.getRunTable();
            _pkColumnName = ft.getPkColumnName();
            _fkColumnName = ft.getFkColumnName();
        }

        @Override
        public FilterStrategy getFilterStrategy(TransformJobContext context, StepMeta stepMeta)
        {
            return new RunFilterStrategy(context, stepMeta, this);
        }

        @Override
        public boolean checkStepsSeparately()
        {
            return false;
        }

        @Override
        public Type getType()
        {
            return Type.Run;
        }
    }
}
