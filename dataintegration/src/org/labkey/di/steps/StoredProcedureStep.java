/*
 * Copyright (c) 2013 LabKey Corporation
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
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
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
    private Map<String, Map<ParamTraits, Integer>> metadataParameters = new CaseInsensitiveHashMap<>();

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

        try
        {
            log.debug("StoredProcedureStep.doWork called");
            if (!executeProcedure())
                getJob().setStatus("ERROR"); // TODO: Should the status be set on exception as well?
            recordWork(action);
        }
        catch (Exception x)
        {
            throw new PipelineJobException(x);
        }
    }

    private boolean executeProcedure() throws SQLException
    {
        if (!validateAndSetDbScope()) return false;
        if (!getParametersFromDbMetadata()) return false;
        seedParameterValues();
        if (!callProcedure()) return false;

        return true;
    }

    private boolean validateAndSetDbScope()
    {
        procSchema = _meta.getTargetSchema().toString();
        procName = _meta.getTargetQuery();
        QuerySchema schema = DefaultSchema.get(_context.getUser(), _context.getContainer(), procSchema);
        if (schema == null || schema.getDbSchema() == null)
        {
            log.error("Schema '" + procSchema + "' does not exist or user does not have permission.");
            return false;
        }
        else scope = schema.getDbSchema().getScope();
        return true;
    }

    private boolean callProcedure() throws SQLException
    {
        int returnValue = 1;
        long duration = 0;
        try (Connection conn = scope.getConnection();
             CallableStatement stmt = conn.prepareCall(buildCallString()); )
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

            returnValue = readOutputParams(stmt, savedParamVals);
            duration = finish - start;
        }
        catch (SQLException e)
        {
           scope.closeConnection();
           throw new SQLException(e);
        }
        if (hasReturn && returnValue > 0)
        {
            log.error("Error: Sproc exited with return code " + returnValue);
            return false;
        }
        log.info("Stored procedure " + procSchema + "." + procName + " completed in " + DateUtil.formatDuration(duration));
        return true;

    }


    private boolean getParametersFromDbMetadata() throws SQLException
    {
        if (scope.getSqlDialect().isPostgreSQL())
            return getParametersFromDbMetadataPSQL();
        else
            return getParametersFromDbMetadataMSSQL();
    }


    // TODO move to dialect
    private boolean getParametersFromDbMetadataPSQL() throws SQLException
    {
        SQLFragment sqlf = new SQLFragment(
                "SELECT NULL AS PROCEDURE_CAT,\n" +
                "        n.nspname AS \"PROCEDURE_SCHEM\",\n" +
                "            p.proname AS \"PROCEDURE_NAME\",\n" +
                "            d.description AS \"REMARKS\",\n" +
                "            array_to_string(p.proargtypes, ';') as \"DATA_TYPES\",\n" +
                "            array_to_string(p.proargnames, ';') AS \"COLUMN_NAMES\"\n" +
                "        FROM pg_catalog.pg_namespace n\n" +
                "        JOIN pg_catalog.pg_proc p ON p.pronamespace=n.oid\n" +
                "        LEFT JOIN pg_catalog.pg_description d ON (p.oid=d.objoid)\n" +
                "        LEFT JOIN pg_catalog.pg_class c ON d.classoid=c.oid\n" +
                "        LEFT JOIN pg_catalog.pg_namespace pn ON c.relnamespace=pn.oid\n" +
                "        WHERE n.nspname ILIKE ? AND p.proname ILIKE ? AND NOT p.proisagg");
        sqlf.add(procSchema);
        sqlf.add(procName);

        /* DOES NOT HANDLE OVERLOADED FUNCTIONS! */
        try (ResultSet rs = (new SqlSelector(scope,sqlf)).getResultSet())
        {
            if (rs.next())
            {
                String data_types = StringUtils.defaultString(rs.getString("DATA_TYPES"),"");
                String column_names = StringUtils.defaultString(rs.getString("COLUMN_NAMES"),"");
                String[] types = data_types.split(";");
                String[] names = column_names.split(";");
                for (int i=0 ; i<Math.min(types.length,names.length) ; i++)
                {
                    if (StringUtils.isNotEmpty(types[i]) && StringUtils.isNotEmpty(names[i]))
                    {
                        Map<ParamTraits, Integer> traitMap = new HashMap<>();
                        traitMap.put(ParamTraits.direction, DatabaseMetaData.procedureColumnIn);
                        traitMap.put(ParamTraits.datatype, Integer.parseInt(types[i]));
                        metadataParameters.put(names[i], traitMap);
                    }
                }
            }
        }
        if (metadataParameters.containsKey("transformRunId"))
        {
            return true;
        }
        else
        {
            log.error("Error: sproc must have transformRunId input parameter");
            return false;
        }
    }


    // TODO move to dialect
    private boolean getParametersFromDbMetadataMSSQL() throws SQLException
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

        if (metadataParameters.containsKey("@transformRunId"))
        {
            return true;
        }
        else
        {
            log.error("Error: sproc must have @transformRunId input parameter");
            return false;
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
        savedParamVals.put("@transformRunId", getTransformJob().getTransformRunId());
        if (savedParamVals.containsKey("@containerId"))
            savedParamVals.put("@containerId", _context.getContainer().getEntityId());

        if (_meta.isUseFilterStrategy())
        {
            FilterStrategy filterStrategy = getFilterStrategy();
            SimpleFilter f = filterStrategy.getFilter(getVariableMap());

            if (savedParamVals.containsKey("@filterRunId"))
                savedParamVals.put("@filterRunId", getVariableMap().get(TransformProperty.IncrementalRunId.getPropertyDescriptor().getName()));
            if (savedParamVals.containsKey("@filterStartTimestamp"))
                savedParamVals.put("@filterStartTimestamp", getVariableMap().get(TransformProperty.IncrementalStartTimestamp.getPropertyDescriptor().getName()));
            if (savedParamVals.containsKey("@filterEndTimestamp"))
                savedParamVals.put("@filterEndTimestamp", getVariableMap().get(TransformProperty.IncrementalEndTimestamp.getPropertyDescriptor().getName()));

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
