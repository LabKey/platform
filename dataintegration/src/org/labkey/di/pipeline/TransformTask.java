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

import org.labkey.api.data.ParameterDescription;
import org.labkey.api.data.TableInfo;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.SystemProperty;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.di.VariableMap;
import org.labkey.di.VariableMapImpl;
import org.labkey.di.data.TransformProperty;
import org.labkey.di.filters.FilterStrategy;
import org.labkey.di.filters.ModifiedSinceFilterStrategy;
import org.labkey.di.steps.StepMeta;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

/**
 * User: daxh
 * Date: 4/17/13
 */
abstract public class TransformTask extends PipelineJob.Task<TransformTaskFactory>
{
    final protected TransformJobContext _context;
    final protected StepMeta _meta;
    final private TransformPipelineJob _txJob;
    final private RecordedActionSet _records = new RecordedActionSet();
    final private VariableMap _variableMap;

    // todo: make these long again but then update the AbstractParameter code
    // or else you'll get a cast exception. Note that when this change is made, StoredProcedureStep will need be updated
    // where it casts the output parameters.
    protected int _recordsInserted = -1;
    protected int _recordsDeleted = -1;
    protected int _recordsModified = -1;

    public static final String INPUT_ROLE = "Row Source";
    public static final String OUTPUT_ROLE = "Row Destination";
    FilterStrategy _filterStrategy = null;

    public TransformTask(TransformTaskFactory factory, PipelineJob job, StepMeta meta)
    {
        this(factory, job, meta, null);
    }

    public TransformTask(TransformTaskFactory factory, PipelineJob job, StepMeta meta, TransformJobContext context)
    {
        super(factory, job);

        _txJob = (TransformPipelineJob)job;
        if (null != _txJob)
            _variableMap = _txJob.getStepVariableMap(meta.getId());
        else
            _variableMap = new VariableMapImpl();
        _context = context;
        _meta = meta;
    }

    public static String getNumRowsString(int rows)
    {
        return rows + " row" + (rows != 1 ? "s" : "");
    }

    protected TransformPipelineJob getTransformJob()
    {
        return _txJob;
    }


    public VariableMap getVariableMap()
    {
        return _variableMap;
    }


    public RecordedActionSet run() throws PipelineJobException
    {
        RecordedAction action = new RecordedAction(_factory.getId().getName());
        action.setStartTime(new Date());

        // Hack up a ViewContext so that we can run trigger scripts
        try (ViewContext.StackResetter ignored = ViewContext.pushMockViewContext(getJob().getUser(), getJob().getContainer(), new ActionURL("dataintegration", "fake.view", getJob().getContainer())))
        {
            QueryService.get().setEnvironment(QueryService.Environment.USER, getJob().getUser());
            QueryService.get().setEnvironment(QueryService.Environment.CONTAINER, getJob().getContainer());

            doWork(action);
            action.setEndTime(new Date());
            addProperties(action);
            action.setRecordCount(_txJob.getRecordCountForAction(action));
            _records.add(action);
            return _records;
        }
        finally
        {
            QueryService.get().clearEnvironment();
        }
    }

    private void addProperties(RecordedAction action)
    {
        for (String key : _variableMap.keySet())
        {
            // Only add entries from the variable map if they were added
            // with a property descriptor.  If the variable does not have
            // a descriptor then we do not want to persist it
            ParameterDescription pd = _variableMap.getDescriptor(key);
            if (pd != null)
            {
                Object value = _variableMap.get(key);
                if (pd instanceof SystemProperty)
                    action.addProperty(((SystemProperty)pd).getPropertyDescriptor(), value);
                if (pd instanceof PropertyDescriptor)
                    action.addProperty((PropertyDescriptor)pd, value);
            }
        }
    }

    public boolean hasWork()
    {
        QuerySchema sourceSchema = DefaultSchema.get(_context.getUser(), _context.getContainer(), _meta.getSourceSchema());
        if (null == sourceSchema || null == sourceSchema.getDbSchema())
            throw new IllegalArgumentException("ERROR: Source schema not found: " + _meta.getSourceSchema());

        TableInfo t = sourceSchema.getTable(_meta.getSourceQuery());
        if (null == t)
            throw new IllegalArgumentException("Could not find table: " +  _meta.getSourceSchema() + "." + _meta.getSourceQuery());

        FilterStrategy filterStrategy = getFilterStrategy();
        return filterStrategy.hasWork();
    }

    protected FilterStrategy getFilterStrategy()
    {
        if (null == _filterStrategy)
        {
            FilterStrategy.Factory factory = null;
            ScheduledPipelineJobDescriptor jd = _context.getJobDescriptor();
            if (jd instanceof TransformDescriptor)
                factory = ((TransformDescriptor)jd).getDefaultFilterFactory();
            if (null == factory)
                factory = new ModifiedSinceFilterStrategy.Factory();

            _filterStrategy = factory.getFilterStrategy(_context, _meta);
        }

        return _filterStrategy;
    }

    abstract public void doWork(RecordedAction action) throws PipelineJobException;

    protected void recordWork(RecordedAction action)
    {
        if (-1 != _recordsInserted)
            action.addProperty(TransformProperty.RecordsInserted.getPropertyDescriptor(), _recordsInserted);
        if (-1 != _recordsDeleted)
            action.addProperty(TransformProperty.RecordsDeleted.getPropertyDescriptor(), _recordsDeleted);
        if (-1 != _recordsModified)
            action.addProperty(TransformProperty.RecordsModified.getPropertyDescriptor(), _recordsModified);
        try
        {
            // input is source table
            // output is dest table, or a stored procedure
            // todo: this is a fake URI, figure out the real story for the Data Input/Ouput for a transform step
            if (_meta.isUseSource())
            {
                action.addInput(new URI(_meta.getSourceSchema() + "." + _meta.getSourceQuery()), TransformTask.INPUT_ROLE);
            }
            action.addOutput(new URI(_meta.getTargetSchema() + "." + _meta.getTargetQuery()), TransformTask.OUTPUT_ROLE, false);
        }
        catch (URISyntaxException ignore)
        {
        }
    }
}
