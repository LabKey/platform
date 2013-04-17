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

import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.sql.SQLException;
import java.util.Date;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class TransformJob extends PipelineJob implements TransformJobSupport
{
    private final BaseQueryTransformDescriptor _etlDescriptor;
    private int _runId;
    private TransformJobContext _transformJobContext;

    public TransformJob(ViewBackgroundInfo info, BaseQueryTransformDescriptor etlDescriptor)
    {
        super(ETLPipelineProvider.NAME, info, PipelineService.get().findPipelineRoot(info.getContainer()));
        _etlDescriptor = etlDescriptor;
        File etlLogDir = getPipeRoot().resolvePath("etlLogs");
        File etlLogFile = new File(etlLogDir, DateUtil.formatDateTime(new Date(), "yyyy-MM-dd HH-mm-ss") + ".etl.log");
        // be sure to set the log file before passing in the logger to the context
        setLogFile(etlLogFile);
        _transformJobContext = new TransformJobContext(etlDescriptor, info.getContainer(), info.getUser(), getLogger());
    }

    public void logRunStart()
    {
        TransformRun run = getTransformRun();
        if (run != null)
        {
            // mark the run as started
            run.setStartTime(new Date());
            update(run);
        }
    }

    public void logRunFinish(int recordCount)
    {

        TransformRun run = getTransformRun();
        if (run != null)
        {
            // Mark that the job has finished successfully
            run.setEndTime(new Date());
            run.setRecordCount(recordCount);
            update(run);
        }
    }

    private void update(TransformRun run)
    {
        try
        {
            run.setStartTime(new Date());
            Table.update(getUser(), DataIntegrationDbSchema.getTransformRunTableInfo(), run, run.getRowId());
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private TransformRun getTransformRun()
    {
        TransformRun run = new TableSelector(DataIntegrationDbSchema.getTransformRunTableInfo(), Table.ALL_COLUMNS, new SimpleFilter(FieldKey.fromParts("RowId"), _runId), null).getObject(TransformRun.class);
        if (run == null)
        {
            getLogger().error("Unable to find database record for run with RowId " + _runId);
            setStatus(ERROR_STATUS);
        }

        return run;
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(TransformJob.class));
    }

    //
    // TransformJobSupport
    //
    public BaseQueryTransformDescriptor getTransformDescriptor()
    {
        return _etlDescriptor;
    }

    public TransformJobContext getTransformJobContext()
    {
        return _transformJobContext;
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return "ETL Job: " + _etlDescriptor.getDescription();
    }

    public void setRunId(int runId)
    {
        _runId = runId;
    }
}
