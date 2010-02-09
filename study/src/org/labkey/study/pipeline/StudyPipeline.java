/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.study.pipeline;

import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineAction;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.study.Study;
import org.labkey.api.module.Module;
import org.labkey.study.model.StudyManager;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.samples.SpecimenController;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;


/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Jan 12, 2006
 * Time: 1:16:44 PM
 */

public class StudyPipeline extends PipelineProvider
{
    public StudyPipeline(Module owningModule)
    {
        super("Study", owningModule);
    }


    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
        if (!context.getContainer().hasPermission(context.getUser(), InsertPermission.class))
            return;

        Study study = StudyManager.getInstance().getStudy(context.getContainer());

        if (study == null)
            return;

        try
        {
            File[] files = directory.listFiles(new FileEntryFilter() {
                public boolean accept(File f)
                {
                    return f.getName().endsWith(".dataset");
                }
            });

            handleDatasetFiles(context, study, directory, files, includeAll);

            files = directory.listFiles(new FileEntryFilter()
            {
                public boolean accept(File f)
                {
                    return f.getName().endsWith(".specimens");
                }
            });

            String actionId = createActionId(SpecimenController.ImportSpecimenData.class, "Import specimen data");
            addAction(actionId, SpecimenController.ImportSpecimenData.class, "Import specimen data", directory, files, false, false, includeAll);
        }
        catch (IOException e)
        {
            throw new UnexpectedException(e);
        }
    }


    private static long lastTime = 0;
    private static final Object timeLock = new Object();

    private static long currentSeconds()
    {
        synchronized(timeLock)
        {
            while (true)
            {
                long sec = System.currentTimeMillis();
                sec -= sec % 1000;
                if (sec != lastTime)
                {
                    lastTime = sec;
                    return sec;
                }
                try {Thread.sleep(500);} catch(InterruptedException x){/* */}
            }
        }
    }


    public static File logForInputFile(File f)
    {
        return new File(f.getPath() + "_" + getTimestamp() + ".log");
    }


    public static String getTimestamp()
    {
        String time = DateUtil.toISO(currentSeconds(), false);
        time = time.replace(":", "-");
        time = time.replace(" ", "_");

        return time;
    }


    public static File lockForDataset(Study study, File f)
    {
        String path = f.getPath();
        return new File(path + "." + "_" + study.getContainer().getRowId() + ".lock");
    }


    private void handleDatasetFiles(ViewContext context, Study study, PipelineDirectory directory, File[] files, boolean includeAll) throws IOException
    {
        List<File> lockFiles = new ArrayList<File>();
        List<File> datasetFiles = new ArrayList<File>();

        for (File f : files)
        {
            File lock = lockForDataset(study, f);
            if (lock.exists())
            {
                if (lock.canRead() && lock.canWrite())
                {
                    lockFiles.add(lock);
                }
            }
            else
            {
                datasetFiles.add(f);
            }
        }

        if (!lockFiles.isEmpty())
        {
            ActionURL urlReset = directory.cloneHref();
            urlReset.setAction(StudyController.ResetPipelineAction.class);
            urlReset.replaceParameter("redirect", context.getActionURL().getLocalURIString());
            urlReset.replaceParameter("path", directory.getPathParameter());

            String actionId = StudyController.ResetPipelineAction.class.getName() + ":Delete lock";
            directory.addAction(new PipelineAction(actionId, "Delete lock", urlReset, lockFiles.toArray(new File[lockFiles.size()]), true));
        }

        files = new File[0];
        if (!datasetFiles.isEmpty())
            files = datasetFiles.toArray(new File[datasetFiles.size()]);

        String actionId = createActionId(StudyController.ImportStudyBatchAction.class, "Import datasets");
        addAction(actionId, StudyController.ImportStudyBatchAction.class, "Import datasets", directory, files, false, false, includeAll);
    }
}
