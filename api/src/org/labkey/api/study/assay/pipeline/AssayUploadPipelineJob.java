/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.api.study.assay.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;

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

    /**
     * @param forceSaveBatchProps whether we need to save the batch properties, or if it's already been handled
     */
    public AssayUploadPipelineJob(AssayRunAsyncContext<ProviderType> context, ViewBackgroundInfo info, @NotNull ExpExperiment batch, boolean forceSaveBatchProps, PipeRoot root, File primaryFile) throws IOException, ExperimentException
    {
        super(context.getProvider().getName(), info, root);
        String baseName = primaryFile.getName();
        if (baseName.contains("."))
        {
            baseName = baseName.substring(0, baseName.lastIndexOf("."));
        }
        findUniqueLogFile(primaryFile, baseName);
        _forceSaveBatchProps = forceSaveBatchProps;

        context.logProperties(getLogger());
        _context = context;
        _batchId = batch.getRowId();
        _primaryFile = primaryFile;
    }

    /** Finds a file name that hasn't been used yet, appending ".2", ".3", etc as needed */
    private void findUniqueLogFile(File primaryFile, String baseName)
    {
        // need to look in both the assay data and archived dirs for any unused log file names (issue 20987)
        File fileLog = FT_LOG.newFile(primaryFile.getParentFile(), baseName);
        File archivedDir = new File(primaryFile.getParentFile(), AssayFileWriter.ARCHIVED_DIR_NAME);
        File fileLogArchived = FT_LOG.newFile(archivedDir, baseName);

        int index = 1;
        while (NetworkDrive.exists(fileLog) || NetworkDrive.exists(fileLogArchived))
        {
            fileLog = FT_LOG.newFile(primaryFile.getParentFile(), baseName + "." + (index));
            fileLogArchived = FT_LOG.newFile(archivedDir, baseName + "." + (index++));
        }
        setLogFile(fileLog);
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
    public String getDescription()
    {
        // Generate a description that matches what the run's name/ID will be
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

    @Override
    public void run()
    {
        try
        {
            _context.setLogger(getLogger());

            setStatus(TaskStatus.running);
            getLogger().info("Starting assay upload");

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
            _context.getProvider().getRunCreator().saveExperimentRun(_context, batch, _run, _forceSaveBatchProps);
            setStatus(TaskStatus.complete);
            getLogger().info("Finished assay upload");
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
        }
    }
}
