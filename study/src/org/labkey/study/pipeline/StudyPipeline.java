/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineAction;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.SpecimenTransform;
import org.labkey.api.study.Study;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.model.StudyManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
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


    public void updateFileProperties(final ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
        if (!context.getContainer().hasPermission(context.getUser(), InsertPermission.class))
            return;

        if (context.getContainer().isDataspace())
            return;         // Cannot import specimens into Dataspace container

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
                    if (SpecimenBatch.ARCHIVE_FILE_TYPE.isType(f))
                        return true;
                    else
                    {
                        for (SpecimenTransform transform : SpecimenService.get().getSpecimenTransforms(context.getContainer()))
                        {
                            if (transform.getFileType().isType(f))
                                return true;
                        }
                    }
                    return false;
                }
            });

            String actionId = createActionId(SpecimenController.ImportSpecimenData.class, "Import Specimen Data");
            addAction(actionId, SpecimenController.ImportSpecimenData.class, "Import Specimen Data", directory, files, true, false, includeAll);
        }
        catch (IOException e)
        {
            throw new UnexpectedException(e);
        }
    }


    public static File logForInputFile(File f, PipeRoot pipeRoot)
    {
        return new File(pipeRoot.getLogDirectory(), FileUtil.makeFileNameWithTimestamp(f.getName(), "log"));
    }


    public static File lockForDataset(Study study, File f)
    {
        String path = f.getPath();
        return new File(path + "." + "_" + study.getContainer().getRowId() + ".lock");
    }

    public static File lockForDataset(Study study, Path path)
    {
        return new File(path + "." + "_" + study.getContainer().getRowId() + ".lock");
    }

    private void handleDatasetFiles(ViewContext context, Study study, PipelineDirectory directory, File[] files, boolean includeAll) throws IOException
    {
        List<File> lockFiles = new ArrayList<>();
        List<File> datasetFiles = new ArrayList<>();

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

        String actionId = createActionId(StudyController.ImportStudyBatchAction.class, "Import Datasets");
        addAction(actionId, StudyController.ImportStudyBatchAction.class, "Import Datasets", directory, files, false, false, includeAll);
    }
}
