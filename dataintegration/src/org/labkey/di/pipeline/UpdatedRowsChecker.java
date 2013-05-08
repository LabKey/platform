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
import org.labkey.api.data.QueryProfiler;
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
import org.labkey.api.writer.ContainerUser;
import org.labkey.di.DataIntegrationDbSchema;
import org.labkey.di.steps.SimpleQueryTransformStep;
import org.labkey.di.steps.SimpleQueryTransformStepMeta;

import java.util.Collections;
import java.util.Date;
import java.util.List;
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
    final private boolean verbose;
    List<SimpleQueryTransformStepMeta> stepMetas;

    public UpdatedRowsChecker(ScheduledPipelineJobDescriptor d, ScheduledPipelineJobContext context,  List<SimpleQueryTransformStepMeta> steps)
    {
        this.d = d;
        this.c = context.getContainer();
        this.user = context.getUser();
        this.stepMetas = steps;
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

    @Override
    public Boolean call() throws Exception
    {
        LOG.debug("Running" + this.getClass().getSimpleName() + " " + this.toString());

        Container c = getContainer();
        if (null == c)
            return false;

        for (SimpleQueryTransformStepMeta stepMeta : stepMetas)
        {
            // TODO : mapping from Step -> StepMeta should not be hard coded
            ContainerUser context = d.getJobContext(getContainer(), getUser());
            TransformTask step = null;

            if (TransformTask.class.equals(stepMeta.getTaskClass()))
                step = new SimpleQueryTransformStep(null, null, stepMeta, (TransformJobContext)context);
            else
            if (TestTask.class.equals(stepMeta.getTaskClass()))
                step = new TestTask(null, null, stepMeta);
            else
            {
                assert false; // how in the heck did we get this far with an unknown class?
                continue;
            }


            if (step.hasWork())
                return true;
        }
        // TODO log somewhere (if verbose then log even if there is no work found)
        return false;
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
