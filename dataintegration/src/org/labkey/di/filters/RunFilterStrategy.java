package org.labkey.di.filters;

import org.labkey.api.data.Aggregate;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.api.etl.CopyConfig;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SchemaKey;
import org.labkey.di.VariableMap;
import org.labkey.di.data.TransformProperty;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformManager;
import org.labkey.di.steps.StepMeta;
import org.labkey.etl.xml.FilterType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: matthew
 * Date: 5/13/13
 * Time: 1:12 PM
 */
public class RunFilterStrategy implements FilterStrategy
{
    final TransformJobContext _context;
    final CopyConfig _config;

    SchemaKey _runTableSchema;
    String _runTableName;
    TableInfo _runsTable;
    String _pkColumnName = null;
    ColumnInfo _runPkCol;

    TableInfo _sourceTable;
    String _fkDefaultColumnName = null;
    ColumnInfo _sourceFkCol;


    public RunFilterStrategy(TransformJobContext context, StepMeta stepMeta, SchemaKey runTableSchema, String runTableName, String pkRunColumnName, String fkRunColumnName)
    {
        if (!(stepMeta instanceof CopyConfig))
            throw new IllegalArgumentException(this.getClass().getName() + " is not compatible with " + stepMeta.getClass().getName());
        _context = context;
        _config = (CopyConfig)stepMeta;
        _runTableName = runTableName;
        _pkColumnName = pkRunColumnName;
        _fkDefaultColumnName = fkRunColumnName;
    }


    public RunFilterStrategy(TransformJobContext context, StepMeta stepMeta, Factory f)
    {
        this(context, stepMeta, f._runTableSchema, f._runTableName, f._pkColumnName, f._fkColumnName);
    }


    private void init()
    {
        if (null != _runPkCol)
            return;

        SchemaKey runSchemaKey = null!=_runTableSchema ? _runTableSchema : _config.getSourceSchema();
        QuerySchema runSchema = DefaultSchema.get(_context.getUser(), _context.getContainer(), runSchemaKey);
        if (null == runSchema)
            throw new IllegalArgumentException("Schema not found: " + runSchemaKey);

        _runsTable = runSchema.getTable(_runTableName);
        if (null == _runsTable)
            throw new IllegalArgumentException("Table not found: " + _runTableName);
        _runPkCol = _runsTable.getColumn(_pkColumnName);
        if (null == _runPkCol)
            throw new IllegalArgumentException("Column not found: " + _runTableName + "." + _runPkCol);


        if (null != _config)
        {
            QuerySchema sourceSchema = DefaultSchema.get(_context.getUser(), _context.getContainer(), _config.getSourceSchema());
            if (null == sourceSchema)
                throw new IllegalArgumentException("Schema not found: " + sourceSchema);

            _sourceTable = sourceSchema.getTable(_config.getSourceQuery());
            if (null == _sourceTable)
                throw new IllegalArgumentException("Table not found: " + _config.getSourceQuery());

            String runColumnName = _fkDefaultColumnName; // TODO StringUtils.defaultString(_config...)
            _sourceFkCol = _sourceTable.getColumn(runColumnName);
            if (null == _sourceFkCol)
                throw new IllegalArgumentException("Column not found: " + _config.getSourceQuery() + "." + runColumnName);
        }
    }


    @Override
    public boolean hasWork()
    {
        init();

        SimpleFilter f = new SimpleFilter();
        Integer lastRunId = getLastSuccessfulRunId();
        if (null != lastRunId)
            f.addCondition(_runPkCol.getFieldKey(), lastRunId, CompareType.GT);

        Aggregate min = new Aggregate(_runPkCol, Aggregate.Type.MIN);

        TableSelector ts = new TableSelector(_runsTable, Collections.singleton(_runPkCol), f, null);
        Map<String, List<Aggregate.Result>> results = ts.getAggregates(Arrays.asList(min));
        List<Aggregate.Result> list = results.get(_runPkCol.getName());
        Aggregate.Result minResult = list.get(0);
        Integer nextRunId = null;
        if (null != minResult.getValue())
            nextRunId = ((Number)minResult.getValue()).intValue();
        return (null != nextRunId);
    }



    @Override
    public SimpleFilter getFilter(VariableMap variables)
    {
        init();

        Integer nextRunId = null;
        Object v = variables.get(TransformProperty.IncrementalRunId.getPropertyDescriptor().getName());

        if (v instanceof Integer)
        {
            nextRunId = (Integer)v;
        }
        else
        {
            SimpleFilter f = new SimpleFilter();
            Integer lastRunId = getLastSuccessfulRunId();
            if (null != lastRunId)
                f.addCondition(_runPkCol.getFieldKey(), lastRunId, CompareType.GT);

            Aggregate min = new Aggregate(_runPkCol, Aggregate.Type.MIN);

            TableSelector ts = new TableSelector(_sourceTable, Collections.singleton(_sourceFkCol), f, null);
            Map<String, List<Aggregate.Result>> results = ts.getAggregates(Arrays.asList(min));
            List<Aggregate.Result> list = results.get(_sourceFkCol.getName());
            Aggregate.Result minResult = list.get(0);
            if (null != minResult.getValue())
                nextRunId = ((Number)minResult.getValue()).intValue();
            variables.put(TransformProperty.IncrementalRunId.getPropertyDescriptor(), nextRunId, VariableMap.Scope.global);
        }

        if (null != nextRunId)
            return new SimpleFilter(_sourceFkCol.getFieldKey(), nextRunId, CompareType.EQUAL);
        else
            return new SimpleFilter(new SimpleFilter.FalseClause());
    }


    Integer getLastSuccessfulRunId()
    {
        // get the experiment run for the last successfully run transform for this configuration
        Integer expRunId = TransformManager.get().getLastSuccessfulTransformExpRun(_context.getTransformId(), _context.getTransformVersion());
        if (null != expRunId)
        {
            VariableMap map = TransformManager.get().getVariableMapForTransformJob(expRunId);
            if (null != map)
            {
                Object o = map.get(TransformProperty.IncrementalRunId.getPropertyDescriptor().getName());
                if (!(o instanceof Number))
                    return null;
                return ((Number)o).intValue();
            }
        }
        return null;
    }


    public static class Factory implements FilterStrategy.Factory
    {
        ScheduledPipelineJobDescriptor _jobDescriptor;
        SchemaKey _runTableSchema = null;
        String _runTableName = null;
        String _pkColumnName = null;
        String _fkColumnName = null;

        public Factory(ScheduledPipelineJobDescriptor jobDescriptor, FilterType ft)
        {
            _jobDescriptor = jobDescriptor;
            // TODO
//            _runSchemaName = ft.getRunSchemaName();
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
    }
}
