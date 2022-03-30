/*
 * Copyright (c) 2012-2018 LabKey Corporation
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
package org.labkey.api.assay.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobNotificationProvider;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Pipeline job for completing the final steps of the assay upload wizard in the background.
 * Runs any transform or validation scripts, and does the actual import
 * User: jeckels
 * Date: Feb 13, 2012
 */
public class AssayUploadPipelineJob<ProviderType extends AssayProvider> extends PipelineJob
{
    private int _batchId;
    private AssayRunAsyncContext<ProviderType> _context;

    private File _primaryFile;
    private boolean _forceSaveBatchProps;
    private ExpRun _run;

    // For serialization
    protected AssayUploadPipelineJob()
    {}

    /**
     * @param forceSaveBatchProps whether we need to save the batch properties, or if it's already been handled
     */
    public AssayUploadPipelineJob(AssayRunAsyncContext<ProviderType> context, ViewBackgroundInfo info, @NotNull ExpExperiment batch, boolean forceSaveBatchProps, PipeRoot root, File primaryFile)
    {
        super(context.getProvider().getName(), info, root);
        String baseName = primaryFile.getName();
        if (baseName.contains("."))
        {
            baseName = baseName.substring(0, baseName.lastIndexOf("."));
        }
        setLogFile(findUniqueLogFile(primaryFile, baseName));
        _forceSaveBatchProps = forceSaveBatchProps;

        context.logProperties(getLogger());
        _context = context;
        _batchId = batch.getRowId();
        _primaryFile = primaryFile;
    }

    @Override
    public URLHelper getStatusHref()
    {
        if (_run != null)
        {
            return PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(_run.getContainer(), _run.getProtocol(), _run.getRowId());
        }
        return null;
    }

    @Override
    public @Nullable TaskPipeline<?> getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(AssayUploadPipelineJob.class));
    }

    @Override
    public String getDescription()
    {
        // Generate a description that matches what the run's name/ID will be
        if (!StringUtils.isEmpty(_context.getJobDescription()))
        {
            return _context.getJobDescription();
        }
        if (_context.getName() != null)
        {
            return _context.getName();
        }
        if (_primaryFile != null)
        {
            return _primaryFile.getName();
        }
        return "Assay upload";
    }

    public void doWork()
    {
        PipelineJobNotificationProvider notificationProvider = getNotificationProvider();

        try
        {
            _context.setLogger(getLogger());

            getLogger().info("Starting assay upload");

            if (notificationProvider != null)
                notificationProvider.onJobStart(this);

            if (_context.getReRunId() != null)
            {
                // Check that the run to be replaced still exists before attempting to do all of the work
                ExpRun expRun = ExperimentService.get().getExpRun(_context.getReRunId().intValue());
                if (expRun == null)
                {
                    throw new ExperimentException("Unable to find referenced run. It may have been deleted or otherwise replaced already. (RowId " + _context.getReRunId() + ")");
                }
            }

            // Create the basic run
            _run = AssayService.get().createExperimentRun(_context.getName(), getContainer(), _context.getProtocol(), _primaryFile);
            _run.setComments(_context.getComments());
            // remember which job created the run so we can show this run on the job details page
            _run.setJobId(PipelineService.get().getJobId(getUser(), getContainer(), getJobGUID()));

            // Find a batch for the run
            ExpExperiment batch = ExperimentService.get().getExpExperiment(_batchId);
            if (batch == null)
            {
                // Batch was deleted already, make a new one
                batch = AssayService.get().createStandardBatch(getContainer(), null, _context.getProtocol());
                batch.save(getUser());

                // Be sure to save batch properties since we had a make a new one
                _forceSaveBatchProps = true;
            }

            // Do all the real work of the import
            ExpExperiment result = _context.getProvider().getRunCreator().saveExperimentRun(_context, batch, _run, _forceSaveBatchProps);
            setStatus(TaskStatus.complete);
            getLogger().info("Finished assay upload");

            if (notificationProvider != null)
            {
                Map<String, Object> results = new HashMap<>();

                results.put("provider", _context.getProvider().getName());
                results.put("assayId", _context.getProtocol().getRowId());
                results.put("assayName", _context.getProtocol().getName());
                results.put("batchId", result.getRowId());
                results.put("runId", _run.getRowId());

                notificationProvider.onJobSuccess(this, results);
            }
        }
        catch (Exception e)
        {
            getLogger().error("The following error was generated by the assay upload:\n");
            // if the error message contains many lines, show it in abbreviated form as the error text (info will have full text)
            if (e.getMessage() != null)
            {
                String[] messageLines = e.getMessage().split("\n");
                if (messageLines.length > 5)
                {
                    StringBuilder sb = new StringBuilder(messageLines[0]);
                    sb.append("\n");
                    sb.append("... [").append(messageLines.length - 5).append(" output lines omitted, see full output in log for details]");
                    sb.append("\n");
                    for (int i = messageLines.length - 4; i < messageLines.length; i++)
                        sb.append(messageLines[i]).append("\n");
                    getLogger().error(sb.toString());
                    setStatus(TaskStatus.error, sb.toString());
                }
                else
                {
                    getLogger().error(e.getMessage() + "\n");
                    setStatus(TaskStatus.error, e.getMessage());
                }
            }
            getLogger().info("Error StackTrace", e);

            if (notificationProvider != null)
                notificationProvider.onJobError(this, e.getMessage());
        }
    }

    private PipelineJobNotificationProvider getNotificationProvider()
    {
        return PipelineService.get().getPipelineJobNotificationProvider(getJobNotificationProvider(), this);
    }

    @Override
    protected String getJobNotificationProvider()
    {
        return _context._jobNotificationProvider;
    }

    public File getPrimaryFile()
    {
        return _primaryFile;
    }

}
