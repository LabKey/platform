/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.CopyConfig;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.security.User;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformTaskFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by matthew on 7/29/15.
 */
public class TruncateTransformStep extends SimpleQueryTransformStep
{
    public TruncateTransformStep(TransformTaskFactory f, PipelineJob job, SimpleQueryTransformStepMeta meta, TransformJobContext context)
    {
        super(f,job,meta,context);
        _validateSource = false;
    }

    @Override
    public boolean hasWork()
    {
        return true;
    }

    @Override
    protected DbScope getSourceScope(QuerySchema sourceSchema, DbScope targetScope)
    {
        throw new IllegalStateException();
    }


    /** NOTE we could override doWork(), but just overriding executeCopy() is pretty surgical, even if though there is not "copy" happening */
    @Override
    public boolean executeCopy(CopyConfig meta, Container c, User u, Logger log) throws IOException, SQLException, PipelineJobException
    {
        boolean validationResult = validate(meta, c, u, log);
        if (!validationResult)
            return false;

        QuerySchema targetSchema = DefaultSchema.get(u, c, meta.getTargetSchema());
        DbScope targetScope = targetSchema.getDbSchema().getScope();

        try
        {
            log.info("Truncating table " + meta.getFullTargetString());
            TableInfo targetTableInfo = targetSchema.getTable(meta.getTargetQuery());
            if (null == targetTableInfo)
            {
                log.error("Could not find table: " +  meta.getFullTargetString());
                return false;
            }
            QueryUpdateService qus = targetTableInfo.getUpdateService();
            if (null == qus)
            {
                log.error("Can't truncate table: " + meta.getFullTargetString());
                return false;
            }

            try
            (
                    DbScope.Transaction txTarget = conditionalGetTransaction(targetScope, _meta.isUseTargetTransaction());
            )
            {
                Map<Enum, Object> options = new HashMap<>();
                options.put(QueryUpdateService.ConfigParameters.Logger, log);
                _recordsDeleted = qus.truncateRows(u, c, options, null);
                if (null != txTarget)
                    txTarget.commit();
            }
            log.info("Deleted " + getNumRowsString(_recordsDeleted) + " from " + meta.getFullTargetString());
            setTargetForURI(meta.getFullTargetString());
            return true;
        }
        catch (BatchValidationException |QueryUpdateServiceException ex)
        {
            throw new RuntimeException(ex);
        }
        catch (SQLException sqlx)
        {
            throw new RuntimeSQLException(sqlx);
        }
        catch (CancelledException x)
        {
            throw x;
        }
        catch (Exception x)
        {
            throw new PipelineJobException("Failed to run truncate step.", x);
        }
    }


    @Override
    protected DataIteratorBuilder selectFromSource(CopyConfig meta, Container c, User u, DataIteratorContext context, Logger log)
    {
        throw new IllegalStateException();
    }
}
