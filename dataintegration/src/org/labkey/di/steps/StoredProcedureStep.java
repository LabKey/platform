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
package org.labkey.di.steps;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialect.MetadataParameterInfo;
import org.labkey.api.data.dialect.SqlDialect.ParamTraits;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.ResultSetDataIterator;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.DateUtil;
import org.labkey.di.TransformDataIteratorBuilder;
import org.labkey.di.VariableMap;
import org.labkey.di.data.TransformProperty;
import org.labkey.di.filters.FilterStrategy;
import org.labkey.di.filters.ModifiedSinceFilterStrategy;
import org.labkey.di.filters.RunFilterStrategy;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformTask;
import org.labkey.di.pipeline.TransformTaskFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * User: tgaluhn
 * Date: 10/7/13
 */
public class StoredProcedureStep extends TransformTask
{
    private final StoredProcedureStepMeta _meta;

    private String procSchema;
    private String procName;
    private DbScope procScope;
    private DbScope targetScope = null;
    private SqlDialect procDialect;
    private RETURN_TYPE procReturns = RETURN_TYPE.NONE;
    private CaseInsensitiveHashMap<Object> localSavedParamVals = new CaseInsensitiveHashMap<>();
    private CaseInsensitiveHashMap<Object> globalSavedParamVals = new CaseInsensitiveHashMap<>();
    private Map<String, MetadataParameterInfo> metadataParameters;

    private static enum RETURN_TYPE
    {
        NONE,
        INTEGER,
        RESULTSET
    }

    private static enum SPECIAL_PARMS
    {
        transformRunId,
        filterRunId,
        filterStartTimestamp,
        filterEndTimestamp,
        containerId,
        rowsInserted,
        rowsDeleted,
        rowsModified,
        rowsRejected,
        rowCount,
        returnMsg,
        debug,
        procVersion,
        return_status, // Underscore for easiest compatibility with SQL Server
        resultSet; // A returned resultset (postgres) vs. inline (SQL Server)

        public static CaseInsensitiveHashSet getSpecialParameterNames()
        {
            CaseInsensitiveHashSet ret = new CaseInsensitiveHashSet();
            for (SPECIAL_PARMS parm : values())
            {
                ret.add(parm.name());
            }
            return ret;
        }
    }

    public StoredProcedureStep(TransformTaskFactory f, PipelineJob job, StoredProcedureStepMeta meta, TransformJobContext context)
    {
        super(f, job, meta, context);
        _meta = meta;
    }

    @Override
    public boolean hasWork()
    {
        FilterStrategy filterStrategy = getFilterStrategy();
        if (_meta.isUseSource() && filterStrategy instanceof ModifiedSinceFilterStrategy)
        {
            return super.hasWork();
        }
        else if (filterStrategy instanceof RunFilterStrategy)
        {
            return filterStrategy.hasWork();
        }
        else
            _meta.setUseFilterStrategy(false);
            return true;
    }

    @Override
    public void doWork(RecordedAction action) throws PipelineJobException
    {
        try
        {
            getJob().debug("StoredProcedureStep.doWork called");
            if (!executeProcedure())
                throw new PipelineJobException("Error running procedure");
            recordWork(action);
        }
        catch (Exception x)
        {
            throw new PipelineJobException(x);
        }
    }

    @Override
    public void recordWork(RecordedAction action)
    {
        super.recordWork(action);
        try
        {
            action.addInput(new URI(_meta.getProcedureSchema() + "." + _meta.getProcedure()), TransformTask.INPUT_ROLE);
        }
        catch (URISyntaxException ignore)
        {
        }

    }

    private boolean executeProcedure() throws SQLException
    {
        if (!validate(_meta, _context.getContainer(), _context.getUser(), getJob().getLogger())) return false;
        if (!validateAndSetDbScopes()) return false;
        if (!getParametersFromDbMetadata()) return false;
        seedParameterValues();
        logFilterValues();
        return callProcedure();

    }

    private boolean validateAndSetDbScopes()
    {
        // TODO: This is misnamed; we're not really doing validation, nor do we ever return false
        procSchema = _meta.getProcedureSchema().toString();
        procName = _meta.getProcedure();

        DbSchema schema = DbSchema.get(procSchema);
        procScope = schema.getScope();
        procDialect = procScope.getSqlDialect();

        if (_meta.getTargetSchema() != null && StringUtils.isNotEmpty(_meta.getTargetSchema().toString()))
        {
            schema = DbSchema.get(_meta.getTargetSchema().toString());
            if (!schema.getScope().equals(procScope))
                targetScope = schema.getScope();
        }
        return true;
    }

    private boolean callProcedure() throws SQLException
    {
        int returnValue = 1;
        long duration = 0;
        boolean badReturn = false;

        try (Connection conn = procScope.getConnection();
             CallableStatement stmt = conn.prepareCall(procDialect.buildProcedureCall(procSchema, procName, metadataParameters.size(), procReturns.equals(RETURN_TYPE.INTEGER), procReturns.equals(RETURN_TYPE.RESULTSET)));
             DbScope.Transaction txProc = _meta.isUseProcTransaction() ?  procScope.ensureTransaction() : null;
             DbScope.Transaction txTarget = (targetScope != null && _meta.isUseTargetTransaction()) ? procScope.ensureTransaction() : null
        )

        {
            procDialect.registerParameters(procScope, stmt, metadataParameters, procReturns.equals(RETURN_TYPE.RESULTSET));

            getJob().info("Executing stored procedure " + procSchema + "." + procName);
            long start = System.currentTimeMillis();

            if (procReturns.equals(RETURN_TYPE.RESULTSET))
                conn.setAutoCommit(false);
            boolean resultsAvailable = stmt.execute();

            long finish = System.currentTimeMillis();

            processInlineResults(stmt, resultsAvailable, txTarget == null ? txProc : txTarget);

            SQLWarning warn = stmt.getWarnings();
            while (warn != null)
            {
                getJob().info(warn.getMessage());
                warn = warn.getNextWarning();
            }

            Integer procResult = readOutputParams(stmt, txTarget == null ? txProc : txTarget);
            if (procResult == null)
                return false;
            else returnValue = procResult;
            if (txProc != null)
                txProc.commit();
            if (txTarget != null)
                txTarget.commit();
            if ((procReturns.equals(RETURN_TYPE.INTEGER)) && returnValue > 0)
                badReturn = true;

            duration = finish - start;
        }
        if (badReturn)
        {
            getJob().error("Error: Sproc exited with return code " + returnValue);
            return false;
        }
        getJob().info("Stored procedure " + procSchema + "." + procName + " completed in " + DateUtil.formatDuration(duration));
        return true;

    }

    /**
     *  For SQL Server. Have to step through all inline resultsets before allowed to access output metadataParameters.
     * Also, the first may be the real resultset we want to write to a target. Postgres result sets are handled later with output metadataParameters
     */
    private boolean processInlineResults(CallableStatement stmt, boolean resultsAvailable, DbScope.Transaction txTarget) throws SQLException
    {
        ResultSet inlineResult;
        boolean firstResult = true;
        if (procDialect.isProcedureSupportsInlineResults())
        {
            while (resultsAvailable || stmt.getUpdateCount() > -1) // row counts of queries internal to the proc are also in the set of resultsets. Nice, huh?
            {
                inlineResult = stmt.getResultSet();
                if (inlineResult != null) // its a real resultset, not just a placeholder for a row count
                {
                    // Only keep the first of the inline result sets.
                    // TODO: might be nice to allow multiple result sets/targets, and also respect debug/verbose flag to log result sets
                    //
                    if (firstResult && _meta.isUseTarget())
                    {
                        firstResult = false;
                        if (!writeToTarget(inlineResult, getJob().getLogger(), txTarget))
                            return false;
                    }
                    else
                        getJob().warn("Warning: more than one inline result set output by stored procedure. Only the first is processed.");
                }
                resultsAvailable = stmt.getMoreResults();
            }
        }

        return true;
    }

    private boolean getParametersFromDbMetadata() throws SQLException
    {
        metadataParameters = procDialect.getParametersFromDbMetadata(procScope, procSchema, procName);

        if (metadataParameters.containsKey(SPECIAL_PARMS.return_status.name()))
            procReturns = RETURN_TYPE.INTEGER;
        else if (metadataParameters.containsKey(SPECIAL_PARMS.resultSet.name()))
            procReturns = RETURN_TYPE.RESULTSET;

        return true;
    }

    private void seedParameterValues()
    {
        boolean missingParam = false;

        Map<String, StoredProcedureStepMeta.ETLParameterInfo> xmlParamInfos= _meta.getXmlParamInfos();

        initSavedParamVals();

        for (Map.Entry<String, MetadataParameterInfo> parameter : metadataParameters.entrySet())
        {
            String paramName = parameter.getKey();
            String dialectParamName = procDialect.translateParameterName(paramName, true);
            MetadataParameterInfo mdParamInfo = parameter.getValue();
            Object inputValue = null;

            if (paramName.equalsIgnoreCase(SPECIAL_PARMS.transformRunId.name()))
            {
                inputValue = getTransformJob().getTransformRunId();
            }
            else if (paramName.equalsIgnoreCase(SPECIAL_PARMS.containerId.name()))
            {
                inputValue = _context.getContainer().getEntityId().toString();
            }
            else if (xmlParamInfos.containsKey(paramName) && ( !(localSavedParamVals.containsKey(dialectParamName) || globalSavedParamVals.containsKey(dialectParamName)) || _meta.isOverrideParam(paramName)))
            {
                // it's either a new one, or the xml value is set to override the persisted value
               inputValue = xmlParamInfos.get(paramName).getValue();
            }
            else if (localSavedParamVals.containsKey(dialectParamName))
            {
                // Use the persisted value.
                inputValue = localSavedParamVals.get(dialectParamName);
            }
            else if (globalSavedParamVals.containsKey(dialectParamName))
            {
                // Use the persisted global value. local has precedence if the same param name exists in both scopes
                inputValue = globalSavedParamVals.get(dialectParamName);
            }
            else if (!xmlParamInfos.containsKey(paramName) && !localSavedParamVals.containsKey(dialectParamName))
            {
                if (SPECIAL_PARMS.getSpecialParameterNames().contains(paramName)) // Initialize for first run with this parameter
                {
                    if (paramName.equalsIgnoreCase(SPECIAL_PARMS.filterStartTimestamp.name()) || paramName.equalsIgnoreCase(SPECIAL_PARMS.filterEndTimestamp.name()))
                        inputValue = null;
                    else if (paramName.equalsIgnoreCase(SPECIAL_PARMS.returnMsg.name()))
                        inputValue = "";
                    else if (paramName.equalsIgnoreCase(SPECIAL_PARMS.debug.name()))
                        inputValue = 0;
                    else
                        inputValue = -1;
                }
                else
                    missingParam = true;
                    //TODO: check for required, report missing.
            }

            // Now process special filter metadataParameters. Note these aren't persisted in the parameter map, but the other "Incremental" entries the ETL engine uses
            if (_meta.isUseFilterStrategy())
            {
                if (paramName.equalsIgnoreCase(SPECIAL_PARMS.filterRunId.name()))
                {
                    inputValue = getVariableMap().get(TransformProperty.IncrementalRunId.getPropertyDescriptor().getName());
                    if (inputValue == null)
                        inputValue = -1;
                }
                else if (paramName.equalsIgnoreCase(SPECIAL_PARMS.filterStartTimestamp.name()))
                    inputValue = getVariableMap().get(TransformProperty.IncrementalStartTimestamp.getPropertyDescriptor().getName());
                else if (paramName.equalsIgnoreCase(SPECIAL_PARMS.filterEndTimestamp.name()))
                    inputValue = getVariableMap().get(TransformProperty.IncrementalEndTimestamp.getPropertyDescriptor().getName());
            }

            if (mdParamInfo.getParamTraits().get(ParamTraits.datatype) == Types.TIMESTAMP)
            {
                inputValue = castStringToTimestamp(inputValue);
            }

            mdParamInfo.setParamValue(inputValue);
            
        }
    }

    private Object castStringToTimestamp(Object inputValue)
    {
        // The formatting for JSON string timestamps we persist are directly castable to sql.timestamp in some cases, but not all.
        if (inputValue != null && inputValue instanceof String)
        {
            try
            {
                inputValue = Timestamp.valueOf(inputValue.toString());
            }
            catch (IllegalArgumentException e)
            {
                inputValue = new Timestamp(Date.parse(inputValue.toString())); // If that still fails, there's nothing more we can do.
            }
        }
        return inputValue;
    }

    private void logFilterValues()
    {
        if (_meta.isUseFilterStrategy())
        {
            FilterStrategy filterStrategy = getFilterStrategy();
            SimpleFilter f = filterStrategy.getFilter(getVariableMap());
            try
            {
                getJob().info(filterStrategy.getClass().getSimpleName() + ": " + (null == f ? "no filter" : f.toSQLString(procDialect)));
            }
            catch (UnsupportedOperationException|IllegalArgumentException x)
            {
                /* oh well */
            }
        }
    }

    private void initSavedParamVals()
    {
        Object o = getVariableMap().get(TransformProperty.Parameters);
        if (null != o)
            localSavedParamVals.putAll((Map) o);

        getVariableMap().put(TransformProperty.Parameters, localSavedParamVals);

        // Trim any local scoped persisted parameters that have been deprecated and no longer part of the procedure,
        // or have been rescoped to global
        Iterator<Map.Entry<String,Object>> it = localSavedParamVals.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<String, Object> e = it.next();

            final String paramName = procDialect.translateParameterName(e.getKey(), false);
            if (!metadataParameters.containsKey(paramName)
                    && !e.getKey().equals(TransformProperty.IncrementalStartTimestamp.getPropertyDescriptor().getName())
                    && !e.getKey().equals(TransformProperty.IncrementalEndTimestamp.getPropertyDescriptor().getName())
                || _meta.isGlobalParam(paramName))
                it.remove();
        }

        Object globalO = getVariableMap().get(TransformProperty.GlobalParameters);
        if (null != globalO)
            globalSavedParamVals.putAll((Map) globalO);
        // Don't trim persisted global parameters. If they're not part of this procedure, there's no way to tell whether or not they're part of others
        getVariableMap().put(TransformProperty.GlobalParameters, globalSavedParamVals, VariableMap.Scope.global );
    }

    @Nullable
    private Integer readOutputParams(CallableStatement stmt, DbScope.Transaction txTarget) throws SQLException
    {
        Integer returnVal;

        if (procReturns.equals(RETURN_TYPE.RESULTSET) && _meta.isUseTarget()) // Postgres resultset output
        {
            if (writeToTarget((ResultSet) stmt.getObject(1), getJob().getLogger(), txTarget))
                returnVal = 0;
            else returnVal = null;
        }
        else returnVal = procDialect.readOutputParameters(procScope, stmt, metadataParameters);

        for (Map.Entry<String, MetadataParameterInfo> parameter : metadataParameters.entrySet())
        {
            String paramName = parameter.getKey();
            MetadataParameterInfo paramInfo = parameter.getValue();
            if (paramInfo.getParamTraits().get(ParamTraits.direction) == DatabaseMetaData.procedureColumnInOut)
            {
                Object value = paramInfo.getParamValue();
                if (paramName.equalsIgnoreCase(SPECIAL_PARMS.rowsInserted.name()) && (Integer)value > -1)
                {
                    _recordsInserted = (Integer)value;
                    getJob().info("Inserted " + getNumRowsString(_recordsInserted));
                }
                else if (paramName.equalsIgnoreCase(SPECIAL_PARMS.rowsDeleted.name()) && (Integer)value > -1)
                {
                    _recordsDeleted = (Integer)value;
                    getJob().info("Deleted " + getNumRowsString(_recordsDeleted));
                }
                else if (paramName.equalsIgnoreCase(SPECIAL_PARMS.rowsModified.name()) && (Integer)value > -1)
                {
                    _recordsModified = (Integer)value;
                    getJob().info("Modified " + getNumRowsString(_recordsModified));
                }
                else if (paramName.equalsIgnoreCase(SPECIAL_PARMS.returnMsg.name()) && StringUtils.isNotEmpty((String) value))
                {
                    getJob().info("Return Msg: " + value);
                }

                // Persist the new value to the correct scope
                String dialectName = procDialect.translateParameterName(paramName, true);
                if (_meta.isGlobalParam(paramName))
                    globalSavedParamVals.put(dialectName, value);
                else
                    localSavedParamVals.put(dialectName, value);
            }

        }

        return returnVal;
    }

    /**
     * Treat the resultset output by the proc the same as a source query output, write it to the specified target
     */
    private boolean writeToTarget(ResultSet rs, Logger log, DbScope.Transaction txTarget)
    {
        Map<Enum, Object> options = new HashMap<>();
        options.put(QueryUpdateService.ConfigParameters.Logger, log);

        DataIteratorContext context = new DataIteratorContext();
        context.setInsertOption(QueryUpdateService.InsertOption.MERGE);
        context.setConfigParameters(options);
        context.setFailFast(true);

        int transformRunId = getTransformJob().getTransformRunId();
        DataIteratorBuilder transformSource = new TransformDataIteratorBuilder(transformRunId, new DataIteratorBuilder.Wrapper(ResultSetDataIterator.wrap(rs, context)), getJob().getLogger(), getTransformJob(), _factory.getStatusName());
        _recordsInserted = appendToTarget(_meta, _context.getContainer(), _context.getUser(), context, transformSource , getJob().getLogger(), txTarget);

        if (context.getErrors().hasErrors())
        {
            for (ValidationException v : context.getErrors().getRowErrors())
            {
                getJob().error(v.getMessage());
            }
            return false;
        }

        return true;
    }
}
