/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialect.MetadataParameterInfo;
import org.labkey.api.data.dialect.SqlDialect.ParamTraits;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.ResultSetDataIterator;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.DateUtil;
import org.labkey.di.VariableMap;
import org.labkey.di.data.TransformProperty;
import org.labkey.di.filters.FilterStrategy;
import org.labkey.di.filters.ModifiedSinceFilterStrategy;
import org.labkey.di.filters.RunFilterStrategy;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformManager;
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

    private String procQuerySchemaName;
    private String procDbSchemaName;
    private String procName;
    private DbScope procScope;
    private DbScope targetScope = null;
    private SqlDialect procDialect;
    private RETURN_TYPE procReturns = RETURN_TYPE.NONE;
    private CaseInsensitiveHashMap<Object> localSavedParamVals = new CaseInsensitiveHashMap<>();
    private CaseInsensitiveHashMap<Object> globalSavedParamVals = new CaseInsensitiveHashMap<>();
    private Map<String, MetadataParameterInfo> metadataParameters;

    private enum RETURN_TYPE
    {
        NONE,
        INTEGER,
        RESULTSET
    }

    private enum SPECIAL_PARAMS
    {
        transformRunId,
        filterRunId,
        filterStartTimestamp,
        filterEndTimestamp,
        filterStartRowversion,
        filterEndRowversion,
        deletedFilterRunId,
        deletedFilterStartTimestamp,
        deletedFilterEndTimestamp,
        deletedFilterStartRowversion,
        deletedFilterEndRowversion,
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
            for (SPECIAL_PARAMS parm : values())
            {
                ret.add(parm.name());
            }
            return ret;
        }
        private static final Map<String, String> TIMESTAMP_PARAMS = new CaseInsensitiveHashMap<String>() {{
            put(filterStartTimestamp.name(), TransformProperty.IncrementalStartTimestamp.getPropertyDescriptor().getName());
            put(filterEndTimestamp.name(), TransformProperty.IncrementalEndTimestamp.getPropertyDescriptor().getName());
            put(filterStartRowversion.name(), TransformProperty.IncrementalStartRowversion.getPropertyDescriptor().getName());
            put(filterEndRowversion.name(), TransformProperty.IncrementalEndRowversion.getPropertyDescriptor().getName());
            put(deletedFilterStartTimestamp.name(), TransformProperty.DeletedIncrementalStartTimestamp.getPropertyDescriptor().getName());
            put(deletedFilterEndTimestamp.name(), TransformProperty.DeletedIncrementalEndTimestamp.getPropertyDescriptor().getName());
            put(deletedFilterStartRowversion.name(), TransformProperty.DeletedIncrementalStartRowversion.getPropertyDescriptor().getName());
            put(deletedFilterEndRowversion.name(), TransformProperty.DeletedIncrementalEndRowversion.getPropertyDescriptor().getName());
            }};
        public static Map<String, String> getTimestampParams() {return TIMESTAMP_PARAMS;}
    }

    StoredProcedureStep(TransformTaskFactory f, PipelineJob job, StoredProcedureStepMeta meta, TransformJobContext context)
    {
        super(f, job, meta, context);
        _meta = meta;
    }

    @Override
    public boolean hasWork()
    {
        FilterStrategy filterStrategy = getFilterStrategy();
        if (_meta.isUseSource() && filterStrategy instanceof ModifiedSinceFilterStrategy)
            return sourceHasWork();
        else if (filterStrategy instanceof RunFilterStrategy)
            return filterStrategy.hasWork();

        _meta.setUseFilterStrategy(false);
        if (_meta.isGating())
            return gateHasWork();
        else
            return !isEtlGatedByStep();
    }

    /**
     * Sets up parameters and executes the procedure, then checks each gating parameter against its noWorkValue.
     * Output parameters are *not* persisted into the saved json state for this ETL, nor will global output parameter
     * values be available to other gating stored procs for their own work checks.
     *
     */
    private boolean gateHasWork()
    {
        // TODO: Verify the proc exists
        boolean hasWork = false;
        try
        {
            prepareExecution(true);
            CaseInsensitiveHashMap<Object> checkVals = new CaseInsensitiveHashMap<>();
            for (Map.Entry<String, StoredProcedureStepMeta.ETLParameterInfo> xmlParam : _meta.getXmlParamInfos().entrySet())
            {
                if (xmlParam.getValue().isGating())
                {
                    String paramName = procDialect.translateParameterName(xmlParam.getKey(), true);
                    Object outputVal;
                    String noWorkValue = xmlParam.getValue().getNoWorkValue();
                    String compareParam = StringUtils.substringBetween(noWorkValue, "${", "}");
                    if (null == compareParam)
                        outputVal = noWorkValue;
                    else
                    {
                        if (!metadataParameters.containsKey(procDialect.translateParameterName(compareParam, false)))
                            throw new ConfigurationException("Bad comparison parameter name: " + compareParam); // TODO: move this check to parsing validation
                        compareParam = procDialect.translateParameterName(compareParam, true);
                        if (VariableMap.Scope.global.equals(xmlParam.getValue().getScope()))
                            outputVal = globalSavedParamVals.get(compareParam);
                        else
                            outputVal = localSavedParamVals.get(compareParam);
                    }
                    if (null == outputVal)
                        return true;
                    else
                        checkVals.put(paramName, outputVal);
                }
            }

            if (!callProcedure(true))
                throw new ConfigurationException("Error calling procedure");

            for (Map.Entry<String, Object> checkVal : checkVals.entrySet())
            {
                Object outputValue;
                if (VariableMap.Scope.global.equals(_meta.getXmlParamInfos().get(procDialect.translateParameterName(checkVal.getKey(), false)).getScope()))
                    outputValue = globalSavedParamVals.get(checkVal.getKey());
                else
                    outputValue = localSavedParamVals.get(checkVal.getKey());

                if (null != outputValue && !outputValue.toString().equals(checkVal.getValue().toString()))  // TODO: boolean parameters?
                {
                    hasWork = true;
                    break;
                }
            }
        }
        catch (SQLException e)
        {
            throw new ConfigurationException("SQL Exception checking for work", e);
        }
        return hasWork;
    }

    @Override
    public void doWork(RecordedAction action) throws PipelineJobException
    {
        try
        {
            debug("StoredProcedureStep.doWork called");
            if (!prepareExecution(false) || !callProcedure(false))
                throw new PipelineJobException("Error running procedure");
            recordWork(action);
        }
        catch (Exception x)
        {
            throw new PipelineJobException("Error processing with stored procedure", x);
        }
    }

    @Override
    public void recordWork(RecordedAction action)
    {
        super.recordWork(action);
        try
        {
            action.addInput(new URI(procQuerySchemaName + "." + procName), TransformTask.INPUT_ROLE);
        }
        catch (URISyntaxException ignore)
        {
        }

    }

    private boolean prepareExecution(boolean checkingForWork) throws SQLException
    {
        if (!checkingForWork && !validate(_meta, _context.getContainer(), _context.getUser(), _txJob.getLogger())) return false;
        setDbScopes(_context.getContainer(), _context.getUser());
        if (!procDialect.isProcedureExists(procScope, procDbSchemaName, procName))
            throw new ConfigurationException("Could not find procedure " + procQuerySchemaName + "." + procName);
        getParametersFromDbMetadata();
        initSavedParamVals(checkingForWork);
        seedParameterValues(checkingForWork);
        return true;
    }

    private void setDbScopes(Container c, User u)
    {
        procQuerySchemaName =  _meta.getProcedureSchema().toString();
        DbSchema procSchema = getSchema(c, u, _meta.getProcedureSchema());
        if (null == procSchema)
            throw new ConfigurationException("Could not resolve procedure schema " + _meta.getProcedureSchema());
        procDbSchemaName = procSchema.getName();
        procName = _meta.getProcedure();
        procScope = procSchema.getScope();
        procDialect = procScope.getSqlDialect();

        if (_meta.getTargetSchema() != null && StringUtils.isNotEmpty(_meta.getTargetSchema().toString()))
        {
            // Could resolve the target schema with the same method as the proc schema, but that's
            // inconsistent with what SimpleQueryTransformStep does.
            QuerySchema targetQuerySchema = DefaultSchema.get(u, c, _meta.getTargetSchema());
            if (null == targetQuerySchema)
                throw new ConfigurationException("Could not resolve target schema " + _meta.getTargetSchema());
            if (!targetQuerySchema.getDbSchema().getScope().equals(procScope))
                targetScope = targetQuerySchema.getDbSchema().getScope();
        }
    }

    private DbSchema getSchema(Container c, User u, SchemaKey querySchemaKey)
    {
        DbSchema schema = null;
        try
        {
            schema = DbSchema.get(querySchemaKey.toString(), DbSchemaType.Module);
        }
        catch (Exception e)
        {
                /* oh well */
        }
        // This would be right way to try first, but for Argos it returns the underlying caisis db schema
        if (null == schema && null != DefaultSchema.get(u, c, querySchemaKey))
            schema = DefaultSchema.get(u, c, querySchemaKey).getDbSchema();

        // If it's still null, not much we can do.
        return schema;
    }

    private boolean callProcedure(boolean checkingForWork) throws SQLException
    {
        int returnValue = 1;
        long duration = 0;
        boolean badReturn = false;

        boolean closeConnection = !procScope.isTransactionActive();
        Connection conn = procScope.getConnection();
        try (CallableStatement stmt = conn.prepareCall(procDialect.buildProcedureCall(procDbSchemaName, procName, metadataParameters.size(), procReturns.equals(RETURN_TYPE.INTEGER), procReturns.equals(RETURN_TYPE.RESULTSET)));
             DbScope.Transaction txProc = conditionalGetTransaction(procScope, _meta.isUseProcTransaction());
             DbScope.Transaction txTarget = conditionalGetTransaction(targetScope, _meta.isUseTargetTransaction())
        )

        {
            procDialect.registerParameters(procScope, stmt, metadataParameters, procReturns.equals(RETURN_TYPE.RESULTSET));

            info("Executing stored procedure " + procQuerySchemaName + "." + procName);
            long start = System.currentTimeMillis();

            if (procReturns.equals(RETURN_TYPE.RESULTSET))
                conn.setAutoCommit(false);
            boolean resultsAvailable = stmt.execute();

            long finish = System.currentTimeMillis();

            processInlineResults(stmt, resultsAvailable, txTarget == null ? txProc : txTarget, checkingForWork);

            SQLWarning warn = stmt.getWarnings();
            while (warn != null)
            {
                info(warn.getMessage());
                warn = warn.getNextWarning();
            }

            Integer procResult = readOutputParams(stmt, txTarget == null ? txProc : txTarget, checkingForWork);
            if (procResult == null)
                return false;
            else returnValue = procResult;
            if (txProc != null && !txProc.isAborted())
                txProc.commit();
            if (txTarget != null && !txTarget.isAborted())
                txTarget.commit();
            if ((procReturns.equals(RETURN_TYPE.INTEGER)) && returnValue > 0)
                badReturn = true;

            duration = finish - start;
        }
        finally
        {
            if (closeConnection)
                conn.close();
        }
        if (badReturn)
        {
            error("Error: Sproc exited with return code " + returnValue);
            return false;
        }
        info("Stored procedure " + procQuerySchemaName + "." + procName + " completed in " + DateUtil.formatDuration(duration));
        return true;
    }

    /**
     *  For SQL Server. Have to step through all inline resultsets before allowed to access output metadataParameters.
     * Also, the first may be the real resultset we want to write to a target. Postgres result sets are handled later with output metadataParameters
     */
    private boolean processInlineResults(CallableStatement stmt, boolean resultsAvailable, DbScope.Transaction txTarget, boolean checkingForWork) throws SQLException
    {
        ResultSet inlineResult;
        boolean firstResult = true;
        if (procDialect.isProcedureSupportsInlineResults())
        {
            while (resultsAvailable || stmt.getUpdateCount() > -1) // row counts of queries internal to the proc are also in the set of resultsets. Nice, huh?
            {
                inlineResult = stmt.getResultSet();
                if (inlineResult != null && !checkingForWork) // its a real resultset, not just a placeholder for a row count
                {
                    // Only keep the first of the inline result sets.
                    // TODO: might be nice to allow multiple result sets/targets, and also respect debug/verbose flag to log result sets
                    //
                    if (firstResult && _meta.isUseTarget())
                    {
                        firstResult = false;
                        if (!writeToTarget(inlineResult, _txJob.getLogger(), txTarget))
                            return false;
                    }
                    else
                        warn("Warning: more than one inline result set output by stored procedure. Only the first is processed.");
                }
                resultsAvailable = stmt.getMoreResults();
            }
        }

        return true;
    }

    private void getParametersFromDbMetadata() throws SQLException
    {
        metadataParameters = procDialect.getParametersFromDbMetadata(procScope, procDbSchemaName, procName);

        if (metadataParameters.containsKey(SPECIAL_PARAMS.return_status.name()))
            procReturns = RETURN_TYPE.INTEGER;
        else if (metadataParameters.containsKey(SPECIAL_PARAMS.resultSet.name()))
            procReturns = RETURN_TYPE.RESULTSET;
    }

    private void seedParameterValues(boolean checkingForWork)
    {
        boolean missingParam = false;

        Map<String, StoredProcedureStepMeta.ETLParameterInfo> xmlParamInfos = _meta.getXmlParamInfos();

        setAndLogFilterValues();

        for (Map.Entry<String, MetadataParameterInfo> parameter : metadataParameters.entrySet())
        {
            String paramName = parameter.getKey();
            String dialectParamName = procDialect.translateParameterName(paramName, true);
            MetadataParameterInfo mdParamInfo = parameter.getValue();
            Object inputValue = null;

            if (paramName.equalsIgnoreCase(SPECIAL_PARAMS.transformRunId.name()))
            {
                inputValue = checkingForWork ? -1 : getTransformJob().getTransformRunId();
            }
            else if (paramName.equalsIgnoreCase(SPECIAL_PARAMS.containerId.name()))
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
                if (SPECIAL_PARAMS.getSpecialParameterNames().contains(paramName)) // Initialize for first run with this parameter
                {
                    if (SPECIAL_PARAMS.getTimestampParams().containsKey(paramName))
                        inputValue = null;
                    else if (paramName.equalsIgnoreCase(SPECIAL_PARAMS.returnMsg.name()))
                        inputValue = "";
                    else if (paramName.equalsIgnoreCase(SPECIAL_PARAMS.debug.name()))
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
                if (paramName.equalsIgnoreCase(SPECIAL_PARAMS.filterRunId.name()))
                {
                    inputValue = getVariableMap().get(TransformProperty.IncrementalRunId.getPropertyDescriptor().getName());
                    if (inputValue == null)
                        inputValue = -1;
                }
                else if (paramName.equalsIgnoreCase(SPECIAL_PARAMS.deletedFilterRunId.name()))
                {
                    inputValue = getVariableMap().get(TransformProperty.DeletedIncrementalRunId.getPropertyDescriptor().getName());
                    if (inputValue == null)
                        inputValue = -1;
                }
                else if (SPECIAL_PARAMS.getTimestampParams().containsKey(paramName))
                    inputValue = getVariableMap().get(SPECIAL_PARAMS.getTimestampParams().get(paramName));
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

    private void setAndLogFilterValues()
    {
        if (_meta.isUseFilterStrategy())
        {
            FilterStrategy filterStrategy = getFilterStrategy();
            // This also sets the values
            SimpleFilter f = filterStrategy.getFilter(getVariableMap());
            try
            {
                _txJob.info(filterStrategy.getLogMessage(null == f ? null : f.toSQLString(procDialect)));
            }
            catch (UnsupportedOperationException|IllegalArgumentException x)
            {
                /* oh well */
            }
        }
    }

    private void initSavedParamVals(boolean checkingForWork)
    {
        if (checkingForWork)
            getParamsFromTransformConfig();
        else
        {
            Object o = getVariableMap().get(TransformProperty.Parameters);
            if (null != o)
                localSavedParamVals.putAll((Map) o);

            getVariableMap().put(TransformProperty.Parameters, localSavedParamVals);

            // Trim any local scoped persisted parameters that have been deprecated and no longer part of the procedure,
            // or have been rescoped to global
            Iterator<Map.Entry<String, Object>> it = localSavedParamVals.entrySet().iterator();
            while (it.hasNext())
            {
                Map.Entry<String, Object> e = it.next();

                final String paramName = procDialect.translateParameterName(e.getKey(), false);
                if (!metadataParameters.containsKey(paramName)
                        && !SPECIAL_PARAMS.getTimestampParams().containsKey(paramName)
                        && !_meta.isGlobalParam(paramName))
                    it.remove();
            }

            Object globalO = getVariableMap().get(TransformProperty.GlobalParameters);
            if (null != globalO)
                globalSavedParamVals.putAll((Map) globalO);
            // Don't trim persisted global parameters. If they're not part of this procedure, there's no way to tell whether or not they're part of others
            getVariableMap().put(TransformProperty.GlobalParameters, globalSavedParamVals, VariableMap.Scope.global);
        }
    }

    private void getParamsFromTransformConfig()
    {
        JSONObject state = TransformManager.get().getTransformConfiguration(_context.getContainer(), _context.getJobDescriptor()).getJsonState();
        if (!state.isEmpty())
        {
            if (state.has(TransformProperty.GlobalParameters))
                globalSavedParamVals.putAll(state.getJSONObject(TransformProperty.GlobalParameters));

            if (state.has("steps"))
            {
                JSONObject steps = state.getJSONObject("steps");
                if (steps.has(_meta.getId()) && steps.getJSONObject(_meta.getId()).has(TransformProperty.Parameters))
                    localSavedParamVals.putAll(steps.getJSONObject(_meta.getId()).getJSONObject(TransformProperty.Parameters));
            }
        }
    }

    @Nullable
    private Integer readOutputParams(CallableStatement stmt, DbScope.Transaction txTarget, boolean checkingForWork) throws SQLException
    {
        Integer returnVal;

        if (procReturns.equals(RETURN_TYPE.RESULTSET) && _meta.isUseTarget()) // Postgres resultset output
        {
            if (checkingForWork || writeToTarget((ResultSet) stmt.getObject(1), _txJob.getLogger(), txTarget))
                returnVal = 0;
            else returnVal = null;
        }
        else returnVal = procDialect.readOutputParameters(procScope, stmt, metadataParameters);

        for (Map.Entry<String, MetadataParameterInfo> parameter : metadataParameters.entrySet())
        {
            String paramName = parameter.getKey();
            MetadataParameterInfo paramInfo = parameter.getValue();
            final Object value = paramInfo.getParamValue();
            final int paramDirection = paramInfo.getParamTraits().get(ParamTraits.direction);
            String dialectName = procDialect.translateParameterName(paramName, true);
            if (paramDirection == DatabaseMetaData.procedureColumnInOut)
            {
                if (paramName.equalsIgnoreCase(SPECIAL_PARAMS.rowsInserted.name()) && (Integer) value > -1)
                {
                    _recordsInserted = (Integer) value;
                    info("Inserted " + getNumRowsString(_recordsInserted));
                }
                else if (paramName.equalsIgnoreCase(SPECIAL_PARAMS.rowsDeleted.name()) && (Integer) value > -1)
                {
                    _recordsDeleted = (Integer) value;
                    info("Deleted " + getNumRowsString(_recordsDeleted));
                }
                else if (paramName.equalsIgnoreCase(SPECIAL_PARAMS.rowsModified.name()) && (Integer) value > -1)
                {
                    _recordsModified = (Integer) value;
                    info("Modified " + getNumRowsString(_recordsModified));
                }
                else if (paramName.equalsIgnoreCase(SPECIAL_PARAMS.returnMsg.name()) && StringUtils.isNotEmpty((String) value))
                {
                    if (null == returnVal || returnVal > 0)
                        error("Return Msg: " + value);
                    info("Return Msg: " + value);
                }

                // Persist the new value to the correct scope
                if (_meta.isGlobalParam(paramName))
                    globalSavedParamVals.put(dialectName, value);
                else
                    localSavedParamVals.put(dialectName, value);
            }

            // Also save the value for input-only global parameters, so they'll also be available to later stored procs
            if (_meta.isGlobalParam(paramName) && !globalSavedParamVals.containsKey(dialectName) && paramDirection == DatabaseMetaData.procedureColumnIn)
                globalSavedParamVals.put(dialectName, value);
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
        DataIteratorBuilder transformSource = createTransformDataIteratorBuilder(transformRunId, new DataIteratorBuilder.Wrapper(ResultSetDataIterator.wrap(rs, context)), _txJob.getLogger());
        _recordsInserted = appendToTarget(_meta, _context.getContainer(), _context.getUser(), context, transformSource, log, txTarget);

        if (context.getErrors().hasErrors())
        {
            for (ValidationException v : context.getErrors().getRowErrors())
            {
                error(v.getMessage());
            }
            return false;
        }

        return true;
    }

    // The null checks below are necessary because there's no job when a gating proc is checked for work

    private void info(String message)
    {
        if (null != _txJob)
            _txJob.info(message);
    }

    private void warn(String message)
    {
        if (null != _txJob)
            _txJob.warn(message);
    }

    private void debug(String message)
    {
        if (null != _txJob)
            _txJob.debug(message);
    }

    private void error(String message)
    {
        if (null != _txJob)
        {
            _txJob.closeWrappingTransactions(true, true);
            _txJob.error(message);
        }
        else throw new IllegalStateException(message);
    }

}
