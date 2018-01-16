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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.AsyncDataIterator;
import org.labkey.api.dataiterator.CopyConfig;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.QueryDataIteratorBuilder;
import org.labkey.api.di.columnTransform.ColumnTransformException;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.di.filters.FilterStrategy;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformTask;
import org.labkey.di.pipeline.TransformTaskFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * User: matthewb
 * Date: 2013-04-16
 * Time: 12:27 PM
 */
public class SimpleQueryTransformStep extends TransformTask
{
    protected final SimpleQueryTransformStepMeta _meta;

    boolean _useAsynchrousQuery = false;

    public SimpleQueryTransformStep(TransformTaskFactory f, PipelineJob job, SimpleQueryTransformStepMeta meta, TransformJobContext context)
    {
        super(f, job, meta, context);
        _meta = meta;
    }

    @Override
    public boolean hasWork()
    {
        return sourceHasWork();
    }

    public void doWork(RecordedAction action) throws PipelineJobException
    {
        try
        {
            getJob().getLogger().debug("SimpleQueryTransformStep.doWork called");
            if (!executeCopy(_meta, _context.getContainer(), _context.getUser(), getJob().getLogger()))
                throw new PipelineJobException("Error running executeCopy");
            recordWork(action);
        }
        catch (CancelledException | PipelineJobException x)
        {
            // Let these through as-is
            throw x;
        }
        catch (Exception x)
        {
            throw new PipelineJobException(x);
        }
    }

    protected DbScope getSourceScope(QuerySchema sourceSchema, DbScope targetScope)
    {
        DbScope sourceScope = sourceSchema.getDbSchema().getScope();
        if (sourceScope.equals(targetScope))
            return null;
        return sourceScope;
    }

    public boolean executeCopy(CopyConfig meta, Container c, User u, Logger log) throws IOException, SQLException, PipelineJobException
    {
        boolean validationResult = validate(meta, c, u, log);
        if (!validationResult)
            return false;

        QuerySchema sourceSchema = DefaultSchema.get(u, c, meta.getSourceSchema());
        QuerySchema targetSchema;
        DbScope targetScope = null;
        // Only resolve targetSchema/scope if target is in db vs file
        if (meta.getTargetType().equals(CopyConfig.TargetTypes.query))
        {
            targetSchema = DefaultSchema.get(u, c, meta.getTargetSchema());
            targetScope = targetSchema.getDbSchema().getScope();
        }
        DbScope sourceScope = getSourceScope(sourceSchema, targetScope);

        Map<Enum, Object> options = new HashMap<>();
        options.put(QueryUpdateService.ConfigParameters.Logger, log);

        DataIteratorContext context = new DataIteratorContext();
        context.setInsertOption(QueryUpdateService.InsertOption.MERGE);
        context.setConfigParameters(options);
        context.setFailFast(true);
        try
        {
            long start = System.currentTimeMillis();

            try (
                    DbScope.Transaction txTarget = conditionalGetTransaction(targetScope, _meta.isUseTargetTransaction());
                    DbScope.Transaction txSource = conditionalGetTransaction(sourceScope, _meta.isUseSourceTransaction())
            )
            {
                log.info("Copying data from " + meta.getSourceSchema() + "." + meta.getSourceQuery() + " to " +
                        meta.getFullTargetString());

                DataIteratorBuilder source = selectFromSource(meta, c, u, context, log);
                if (null == source)
                    return false;
                int transformRunId = getTransformJob().getTransformRunId();
                DataIteratorBuilder transformSource = createTransformDataIteratorBuilder(transformRunId, source, log);

                _recordsInserted = appendToTarget(meta, c, u, context, transformSource, log, txTarget);

                if (null != txTarget && !context.getErrors().hasErrors() && !txTarget.isAborted())
                    txTarget.commit();
                if (null != txSource && !context.getErrors().hasErrors() && !txSource.isAborted())
                    txSource.commit();
            }
            long finish = System.currentTimeMillis();
            log.info("Copied " + getNumRowsString(_recordsInserted) + " in " + DateUtil.formatDuration(finish - start));
            if (_recordsDeleted > 0)
                log.info("Deleted " + getNumRowsString(_recordsDeleted));
        }
        catch (CancelledException x)
        {
            throw x;
        }
        catch (Exception x)
        {
            // TODO: more verbose logging

            if (ColumnTransformException.class.isInstance(x))
            {
                throw new PipelineJobException("ColumnTransformException during run: " + x.getMessage(), x.getCause());
            }
            else
                throw new PipelineJobException("Failed to run transform from source.", x);
        }
        if (context.getErrors().hasErrors())
        {
            _txJob.closeWrappingTransactions(true,true);
            for (ValidationException v : context.getErrors().getRowErrors())
            {
                log.error(v.getMessage());
            }
            return false;
        }
        return true;
    }

    /** @return null if there is an error parsing the source query */
    @Nullable
    protected DataIteratorBuilder selectFromSource(CopyConfig meta, Container c, User u, DataIteratorContext context, Logger log)
    {
        try
        {
            QuerySchema sourceSchema = DefaultSchema.get(u, c, meta.getSourceSchema());
            TableInfo q = sourceSchema.getTable(meta.getSourceQuery());   // validate source query
            FilterStrategy filterStrategy = getFilterStrategy();
            SimpleFilter f = filterStrategy.getFilter(getVariableMap());
            Map<String,Object> parameters = new HashMap<>();

            // parameters
            for (QueryService.ParameterDecl pd : q.getNamedParameters())
            {
                Object v = getVariableMap().get(pd.getName());
                if (null != v)
                    parameters.put(pd.getName(),v);
            }

            try
            {
                log.info(filterStrategy.getLogMessage(null == f ? null  : f.toSQLString(sourceSchema.getDbSchema().getSqlDialect())));
            }
            catch (UnsupportedOperationException|IllegalArgumentException x)
            {
                /* oh well */
            }

            DataIteratorBuilder source = new QueryDataIteratorBuilder(sourceSchema, meta.getSourceQuery(), null, f);
            ((QueryDataIteratorBuilder)source).setParameters(parameters);

            if (null != meta.getSourceContainerFilter())
            {
                ((QueryDataIteratorBuilder)source).setContainerFilter(meta.getSourceContainerFilter());
            }

            if (null != meta.getSourceColumns())
            {
                ((QueryDataIteratorBuilder)source).setColumns(meta.getSourceColumns());
            }

            if (_useAsynchrousQuery)
                source = new AsyncDataIterator.Builder(source);
            return source;
        }
        catch (QueryParseException x)
        {
            log.error(x.getMessage());
            return null;
        }
    }

}
