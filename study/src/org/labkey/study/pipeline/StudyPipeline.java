/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.security.ACL;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.study.Study;
import org.labkey.api.module.Module;
import org.labkey.study.model.StudyManager;

import java.io.File;
import java.io.IOException;
import java.util.List;


/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Jan 12, 2006
 * Time: 1:16:44 PM
 */

public class StudyPipeline extends PipelineProvider
{
    private static final Logger _log = Logger.getLogger(StudyPipeline.class);

    public StudyPipeline(Module owningModule)
    {
        super("Study", owningModule);
    }


    public void updateFileProperties(ViewContext context, PipeRoot pr, List<FileEntry> entries)
    {
        if (!context.hasPermission(ACL.PERM_INSERT))
            return;

        Study study = StudyManager.getInstance().getStudy(context.getContainer());

        if (study == null)
            return;

        try
        {
            PipeRoot root = PipelineService.get().findPipelineRoot(context.getContainer());
            File rootDir = root.getRootPath();

            for (FileEntry entry : entries)
            {
                File[] files = entry.listFiles(new FileEntryFilter() {
                    public boolean accept(File f)
                    {
                        return f.getName().endsWith(".dataset");
                    }
                });

                if (files != null)
                    handleDatasetFiles(context, study, entry, rootDir, files);

                files = entry.listFiles(new FileEntryFilter()
                {
                    public boolean accept(File f)
                    {
                        return f.getName().endsWith(".specimens");
                    }
                });

                if (files != null)
                    handleSpecimenFiles(entry, rootDir, files);
            }
        }
        catch (Exception e)
        {
            _log.error("Exception", e);
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


    private void handleDatasetFiles(ViewContext context, Study study, FileEntry entry, File rootDir, File[] files) throws IOException
    {
        for (File f : files)
        {
            File lock = lockForDataset(study, f);
            if (lock.exists())
            {
                ActionURL urlReset = entry.cloneHref();
                urlReset.setPageFlow("Study");
                urlReset.setAction("resetPipeline");
                urlReset.replaceParameter("redirect", context.getActionURL().getLocalURIString());
                String path = FileUtil.relativize(rootDir, lock, true);
                urlReset.replaceParameter("path", path);
                if (lock.canRead() && lock.canWrite())
                    entry.addAction(new FileAction("Delete lock", urlReset, new File[]{lock}));
            }
            else
            {
                ActionURL urlImport = entry.cloneHref();
                urlImport.setPageFlow("Study");
                urlImport.setAction("importStudyBatch");
                String path = FileUtil.relativize(rootDir, f, true);
                urlImport.replaceParameter("path", path);
                entry.addAction(new FileAction("Import datasets", urlImport, new File[]{f}));
            }
        }
    }


    private void handleSpecimenFiles(FileEntry entry, File rootDir, File[] files) throws IOException
    {
        for (File f : files)
        {
            ActionURL urlImport = entry.cloneHref();
            urlImport.setPageFlow("Study-Samples");
            urlImport.setAction("importSpecimenData");
            String path = FileUtil.relativize(rootDir, f, true);
            urlImport.replaceParameter("path", path);
            entry.addAction(new FileAction("Import specimen data", urlImport, new File[]{f}));
        }
    }
}
