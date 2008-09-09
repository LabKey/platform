/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.pipeline.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.data.Container;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.DateUtil;
import org.labkey.experiment.XarExporter;
import org.labkey.experiment.LSIDRelativizer;
import org.labkey.experiment.DataURLRelativizer;
import org.labkey.experiment.XarReader;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.ExpRunImpl;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.fhcrc.cpas.exp.xml.ExperimentArchiveDocument;
import org.fhcrc.cpas.exp.xml.ExperimentArchiveType;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: jeckels
 * Date: Feb 14, 2007
 */
public class MoveRunsPipelineJob extends PipelineJob
{
    private final int[] _runIds;
    private Container _sourceContainer;

    public MoveRunsPipelineJob(ViewBackgroundInfo info, Container sourceContainer, int[] runIds) throws SQLException, IOException
    {
        super(ExperimentPipelineProvider.NAME, info);
        _runIds = runIds;
        _sourceContainer = sourceContainer;
        PipeRoot root = PipelineService.get().findPipelineRoot(info.getContainer());
        if (!NetworkDrive.exists(root.getRootPath()))
        {
            throw new FileNotFoundException("Could not find pipeline root on disk: " + root.getRootPath());
        }
        File moveRunLogsDir = ExperimentPipelineProvider.getMoveDirectory(root);
        moveRunLogsDir.mkdirs();
        File logFile = File.createTempFile("moveRun", ".log", moveRunLogsDir);
        setLogFile(logFile);

        StringBuilder sb = new StringBuilder();
        sb.append(getDescription());
        sb.append("\n");
        for (int runId : _runIds)
        {
            ExpRun run = ExperimentService.get().getExpRun(runId);
            if (run == null)
            {
                sb.append("No run found for RunId ");
                sb.append(runId);
            }
            else
            {
                sb.append(run.getLSID());
                sb.append(", id = ");
                sb.append(runId);
            }
            sb.append("\n");
        }
        getLogger().info(sb.toString());
    }

    public String getDescription()
    {
        return "Move " + _runIds.length + " run" + (_runIds.length == 1 ? "" : "s") + " from " + _sourceContainer.getPath() + " to " + getContainer().getPath();
    }

    public ActionURL getStatusHref()
    {
        return null;
    }

    public int[] getRunIds()
    {
        return _runIds;
    }

    public Container getSourceContainer()
    {
        return _sourceContainer;
    }

    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(MoveRunsPipelineJob.class));
    }
}
