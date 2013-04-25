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
package org.labkey.di.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.di.ScheduledPipelineJobContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.di.DataIntegrationDbSchema;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * User: jeckels
 * Date: 3/13/13
 */
public class UpdatedRowsChecker implements Callable<Boolean>
{
    private static final Logger LOG = Logger.getLogger(UpdatedRowsChecker.class);

    final private ScheduledPipelineJobDescriptor d;
    final private Container c;
    final private User user;
    final private SchemaKey sourceSchemaName;
    final private String sourceQueryName;
    final private boolean verbose;

    public UpdatedRowsChecker(ScheduledPipelineJobDescriptor d, ScheduledPipelineJobContext context, SchemaKey sourceSchemaName, String sourceQueryName)
    {
        this.d = d;
        this.c = context.getContainer();
        this.user = context.getUser();
        this.sourceSchemaName = sourceSchemaName;
        this.sourceQueryName = sourceQueryName;
        this.verbose = context.isVerbose();
    }

    public Container getContainer()
    {
        return c;
    }

    public User getUser()
    {
        return user;
    }

    public SchemaKey getSourceSchemaName()
    {
        return sourceSchemaName;
    }

    public String getSourceQueryName()
    {
        return sourceQueryName;
    }


    @Override
    public Boolean call() throws Exception
    {
        LOG.debug("Running" + this.getClass().getSimpleName() + " " + this.toString());

        Container c = getContainer();
        if (null == c)
            return false;

        UserSchema schema = QueryService.get().getUserSchema(getUser(), c, getSourceSchemaName());
        if (schema == null)
        {
            LOG.warn("Unable to find schema " + getSourceSchemaName() + " in " + c.getPath());
            return false;
        }

        TableInfo tableInfo = schema.getTable(getSourceQueryName());
        if (tableInfo == null)
        {
            LOG.warn("Unable to find query " + getSourceQueryName() + " in schema " + getSourceSchemaName() + " in " + c.getPath());
            return false;
        }

        FieldKey modifiedFieldKey = FieldKey.fromParts("modified");
        Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(tableInfo, Collections.singleton(modifiedFieldKey));
        if (columns.isEmpty())
        {
            LOG.warn("Could not find Modified column on query " + getSourceQueryName() + " in schema " + getSourceSchemaName() + " in " + c.getPath());
            return false;
        }

        SimpleFilter filter = new SimpleFilter();
        Date mostRecentRun = getMostRecentRun();
        if (mostRecentRun != null)
        {
            filter.addCondition(modifiedFieldKey, mostRecentRun, CompareType.GTE);
        }
        long updatedRows = new TableSelector(tableInfo, columns.values(), filter, null).getRowCount();

        // TODO log somewhere (if verbose log even if there is no work found)

        return updatedRows > 0;
    }


    public Date getMostRecentRun()
    {
        SQLFragment sql = new SQLFragment("SELECT MAX(StartTime) FROM ");
        sql.append(DataIntegrationDbSchema.getTransformRunTableInfo(), "tr");
        // TODO: need to be able to tell successful jobs from failed jobs
        sql.append(" WHERE Container = ? AND TransformId = ? AND TransformVersion = ?"); //  AND Status='Complete'");
        sql.add(getContainer());
        sql.add(d.getId());
        sql.add(d.getVersion());
        return new SqlSelector(DataIntegrationDbSchema.getSchema(), sql).getObject(Date.class);
    }
}
