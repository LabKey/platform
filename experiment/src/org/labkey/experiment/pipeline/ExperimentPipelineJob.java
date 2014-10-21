/*
 * Copyright (c) 2005-2014 LabKey Corporation
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

package org.labkey.experiment.pipeline;

import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.CompressedXarSource;
import org.labkey.api.exp.FileXarSource;
import org.labkey.api.util.PageFlowUtil;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.sql.BatchUpdateException;
import java.util.List;

/**
 * User: jeckels
 * Date: Oct 26, 2005
 */
public class ExperimentPipelineJob extends PipelineJob
{
    private static Logger _log = Logger.getLogger(ExperimentPipelineJob.class);

    private static final Object _experimentLock = new Object();

    private final File _xarFile;
    private final String _description;
    private final boolean _deleteExistingRuns;

    private transient XarSource _xarSource;

    public ExperimentPipelineJob(ViewBackgroundInfo info, File file, String description, boolean deleteExistingRuns, PipeRoot root) throws IOException
    {
        super(ExperimentPipelineProvider.NAME, info, root);
        _xarFile = file;
        _description = description + " - " + file.getName();
        _deleteExistingRuns = deleteExistingRuns;

        _xarSource = createXarSource(file);
        setLogFile(_xarSource.getLogFile());

        header("XAR Import from " + _xarSource.toString());
    }

    protected XarSource createXarSource(File file)
    {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".xar") || name.endsWith(".zip"))
        {
            return new CompressedXarSource(file, this);
        }
        else
        {
            return new FileXarSource(file, this);
        }
    }

    private XarSource getXarSource()
    {
        if (_xarSource == null)
        {
            _xarSource = createXarSource(_xarFile);

            try
            {
                setLogFile(_xarSource.getLogFile());
            }
            catch (IOException e)
            {
                _log.error("Failed to get log file for " + _xarFile, e);
            }
        }
        return _xarSource;
    }

    public ActionURL getStatusHref()
    {
        ExpRun run = getXarSource().getExperimentRun();
        if (run != null)
        {
            return PageFlowUtil.urlProvider(ExperimentUrls.class).getRunGraphURL(run);
        }
        return null;
    }

    public String getDescription()
    {
        return _description;
    }

    public static boolean loadExperiment(PipelineJob job, XarSource source, boolean deleteExistingRuns)
    {
        try
        {
            source.init();
        }
        catch (Exception e)
        {
            job.getLogger().error("Failed to initialize XAR source", e);
            return false;
        }

        synchronized (_experimentLock)
        {
            job.getLogger().info("Starting to import XAR");

            try
            {
                List<ExpRun> runs = ExperimentService.get().importXar(source, job, deleteExistingRuns);
                if (!runs.isEmpty())
                {
                    source.setExperimentRunRowId(runs.get(0).getRowId());
                }

                job.getLogger().info("");
                job.getLogger().info("XAR import completed successfully");
            }
            catch (Throwable t)
            {
                job.getLogger().info("");
                job.getLogger().fatal("Exception during import", t);
                job.getLogger().fatal("XAR import FAILED");
                if (t instanceof BatchUpdateException)
                {
                    job.getLogger().fatal("Underlying exception", ((BatchUpdateException)t).getNextException());
                }
                return false;
            }
            return true;
        }
    }

    public void run()
    {
        if (!setStatus("LOADING EXPERIMENT"))
            return;

        if (loadExperiment(this, getXarSource(), _deleteExistingRuns))
            setStatus(TaskStatus.complete);
    }
}
