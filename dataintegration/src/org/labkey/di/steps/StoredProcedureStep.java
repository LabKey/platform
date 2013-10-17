package org.labkey.di.steps;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.util.DateUtil;
import org.labkey.di.data.TransformProperty;
import org.labkey.di.filters.FilterStrategy;
import org.labkey.di.filters.ModifiedSinceFilterStrategy;
import org.labkey.di.filters.RunFilterStrategy;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformTask;
import org.labkey.di.pipeline.TransformTaskFactory;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Types;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * User: tgaluhn
 * Date: 10/7/13
 */
public class StoredProcedureStep extends TransformTask
{
    final StoredProcedureStepMeta _meta;

    private String procSchema;
    private String procName;
    private DbScope scope;
    private int paramCount;
    private boolean hasReturn = false;
    private Map<String, Object> savedParamVals = new HashMap<>();
    private Map<String, Map<ParamTraits, Integer>> metadataParameters = new HashMap<>();

    Logger log;

    private enum ParamTraits
    {
        direction,
        datatype,
        required;
    }

    // TODO: Convert these to constants
    private List<String> specialParameters = Arrays.asList("@transformRunId", "@filterRunId", "@filterStartTimestamp", "@filterEndTimestamp", "@containerId",
                                                            "@rowsInserted", "@rowsDeleted", "@rowsModified", "@rowsRejected",
                                                            "@rowCount", "@returnMsg", "@debug");

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
        log = getJob().getLogger();
        procSchema = _meta.getTargetSchema().toString();
        procName = _meta.getTargetQuery();

        try
        {
            log.debug("StoredProcedureStep.doWork called");
            scope = DefaultSchema.get(_context.getUser(), _context.getContainer(), procSchema).getDbSchema().getScope();
            if (!executeProcedure())
                getJob().setStatus("ERROR");
            recordWork(action);
        }
        catch (Exception x)
        {
            log.error(x);
        }
    }


    private boolean executeProcedure() throws SQLException
    {
        getParametersFromDbMetadata();
        seedParameterValues();
        if (!callProcedure()) return false;

        return true;
    }

    private boolean callProcedure() throws SQLException
    {
        try (Connection conn = scope.getConnection();
             CallableStatement stmt = conn.prepareCall(buildCallString()) )
        {
            if (hasReturn)
                stmt.registerOutParameter("@return_status", Types.INTEGER);
            for (String paramName : metadataParameters.keySet())
            {
                Map<ParamTraits, Integer> procParam = metadataParameters.get(paramName);
                int datatype = procParam.get(ParamTraits.datatype);
                stmt.setObject(paramName, savedParamVals.get(paramName), datatype);
                if (procParam.get(ParamTraits.direction) == DatabaseMetaData.procedureColumnInOut)
                {
                    stmt.registerOutParameter(paramName, datatype);
                }
            }

            log.info("Executing stored procedure " + procSchema + "." + procName);
            long start = System.currentTimeMillis();
            if (_meta.isUseTransaction())
                scope.ensureTransaction(Connection.TRANSACTION_SERIALIZABLE);
            boolean resultsAvailable = stmt.execute();
            if (_meta.isUseTransaction())
                scope.commitTransaction();
            long finish = System.currentTimeMillis();
            while (resultsAvailable || stmt.getUpdateCount() > -1)
            {
                /*
                if (_context.isVerbose()) //TODO: verbose flag doesn't seem to be set properly, always false. Investigate, and/or maybe use the @debug flag
                {
                    ResultSet rs = stmt.getResultSet(); // TODO: for now, dropping them on the floor. Eventually would like option to log these.
                    // log it
                }
                else */
                    stmt.getResultSet(); // just to advance

                resultsAvailable = stmt.getMoreResults();
            }

            SQLWarning warn = stmt.getWarnings();
            while (warn != null)
            {
                log.debug(warn.getMessage());
                warn = warn.getNextWarning();
            }

            int returnValue = readOutputParams(stmt, savedParamVals);
            if (returnValue > 0)
            {
                log.error("Error: Sproc exited with return code " + returnValue);
                return false;
            }
            log.info("Stored procedure " + procSchema + "." + procName + " completed in " + DateUtil.formatDuration(finish - start));
            return true;
        }
        catch (SQLException e)
        {
           throw new SQLException(e);
        }
    }

    private void getParametersFromDbMetadata() throws SQLException //TODO: Check has @runId
    {
        try (Connection conn = scope.getConnection();
             ResultSet rs = conn.getMetaData().getProcedureColumns(scope.getDatabaseName(),procSchema, procName, null);)
        {
            while (rs.next())
            {
                if (rs.getInt("COLUMN_TYPE") == DatabaseMetaData.procedureColumnReturn)
                {
                    hasReturn = true;
                }
                else
                {
                    Map<ParamTraits, Integer> traitMap = new HashMap<>();
                    traitMap.put(ParamTraits.direction, rs.getInt("COLUMN_TYPE"));
                    traitMap.put(ParamTraits.datatype, rs.getInt("DATA_TYPE"));
                    //traitMap.put(ParamTraits.required, )
                    metadataParameters.put(rs.getString("COLUMN_NAME"), traitMap);
                }
            }
        }
        catch (SQLException e)
        {
            throw new SQLException(e);
        }
    }

    private void seedParameterValues()
    {
        boolean missingParam = false;

        Map<String, String> xmlParamValues = _meta.getXmlParamValues();

        Object o = getVariableMap().get(TransformProperty.Parameters);
        if (null != o)
            savedParamVals = (Map)o;
        getVariableMap().put(TransformProperty.Parameters, savedParamVals);

        // Trim any parameters that have been deprecated and no longer part of the procedure
        Iterator<Map.Entry<String,Object>> it = savedParamVals.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<String, Object> e = it.next();
            if (!metadataParameters.containsKey(e.getKey())
                    && !e.getKey().equals(TransformProperty.IncrementalStartTimestamp.getPropertyDescriptor().getName())
                    && !e.getKey().equals(TransformProperty.IncrementalEndTimestamp.getPropertyDescriptor().getName()))
                it.remove();
        }

        for (String paramName : metadataParameters.keySet())
        {
            paramCount++;

            if (xmlParamValues.containsKey(paramName) && (!savedParamVals.containsKey(paramName) || _meta.isOverrideParam(paramName)))
            {
                // it's either a new one, or the xml value is set to override the persisted value
                savedParamVals.put(paramName, xmlParamValues.get(paramName));
            }
            else if (!xmlParamValues.containsKey(paramName) && !savedParamVals.containsKey(paramName))
            {
                if (specialParameters.contains(paramName)) // Initialize for first run with this parameter
                {
                    if (paramName.equals("@filterStartTimestamp"))
                        savedParamVals.put(paramName, null);
                    else if (paramName.equals("@filterEndTimestamp"))
                        savedParamVals.put(paramName, null);
                    else if (paramName.equals("@returnMsg"))
                        savedParamVals.put(paramName, "");
                    else if (paramName.equals("@debug"))
                        savedParamVals.put(paramName, 0);
                    else savedParamVals.put(paramName, -1);
                }
                else
                    missingParam = true;
                    //TODO: check for required, report missing.
            }
        }
        // Now process the special parameters.
        // TODO: Bomb if no @runId param
        savedParamVals.put("@runId", getTransformJob().getTransformRunId());
        if (savedParamVals.containsKey("@containerId"))
            savedParamVals.put("@containerId", _context.getContainer().getEntityId());

        if (_meta.isUseFilterStrategy())
        {
            FilterStrategy filterStrategy = getFilterStrategy();
            SimpleFilter f = filterStrategy.getFilter(getVariableMap());

            if (savedParamVals.containsKey("@filterRunId"))
                savedParamVals.put("@filterRunId", savedParamVals.get(TransformProperty.IncrementalRunId.getPropertyDescriptor().getName()));
            if (savedParamVals.containsKey("@filterStartTimestamp"))
                savedParamVals.put("@filterStartTimestamp", savedParamVals.get(TransformProperty.IncrementalStartTimestamp.getPropertyDescriptor().getName()));
            if (savedParamVals.containsKey("@filterEndTimestamp"))
                savedParamVals.put("@filterEndTimestamp", savedParamVals.get(TransformProperty.IncrementalEndTimestamp.getPropertyDescriptor().getName()));

            try
            {
                log.info(filterStrategy.getClass().getSimpleName() + ": " + (null == f ? "no filter"  : f.toSQLString(scope.getSqlDialect())));
            }
            catch (UnsupportedOperationException|IllegalArgumentException x)
            {
                /* oh well */
            }
        }

    }

    private int readOutputParams(CallableStatement stmt, Map<String, Object> savedParamVals) throws SQLException
    {
        for (String paramName : metadataParameters.keySet())
        {
            Map<ParamTraits, Integer> procParam = metadataParameters.get(paramName);
            if (procParam.get(ParamTraits.direction) == DatabaseMetaData.procedureColumnInOut)
            {
                Object value = stmt.getObject(paramName);
                savedParamVals.put(paramName, value);
                if (paramName.equals("@rowsInserted") && (Integer)value > -1)
                {
                    _recordsInserted = (Integer)value;
                    log.info("Inserted " + getNumRowsString(_recordsInserted));
                }
                else if (paramName.equals("@rowsDeleted") && (Integer)value > -1)
                {
                    _recordsDeleted = (Integer)value;
                    log.info("Deleted " + getNumRowsString(_recordsDeleted));
                }
                else if (paramName.equals("@rowsModified") && (Integer)value > -1)
                {
                    _recordsModified = (Integer)value;
                    log.info("Modified " + getNumRowsString(_recordsModified));
                }
                else if (paramName.equals("@returnMsg") && StringUtils.isNotEmpty((String)value))
                {
                    log.info("Return Msg: " + value);
                }
            }
        }
        if (hasReturn)
        {
            return stmt.getInt("@return_status");
        }
        else
            return 0;
    }

    private String buildCallString()
    {
        StringBuilder sb = new StringBuilder();
        if (hasReturn)
        {
            sb.append("? = ");
        }
        sb.append("CALL " + procSchema + "." + procName);
        if (paramCount > 0)
            sb.append("(");
        for (int i = 0; i < paramCount; i++)
        {
            sb.append("?,");
        }
        if (paramCount > 0)
            sb.append(")");

        return sb.toString();
    }
}
