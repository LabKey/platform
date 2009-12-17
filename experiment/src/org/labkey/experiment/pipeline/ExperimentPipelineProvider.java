/*
 * Copyright (c) 2005-2009 LabKey Corporation
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
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.view.ViewContext;
import org.labkey.api.module.Module;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.File;

/**
 * User: jeckels
 * Date: Oct 25, 2005
 */
public class ExperimentPipelineProvider extends PipelineProvider
{
    public static final String NAME = "Experiment";

    private static final String DIR_NAME_EXPERIMENT = "experiment";
    private static final String DIR_NAME_UPLOAD = "UploadedXARs";
    private static final String DIR_NAME_MOVE = "moveRunLogs";

    public static File getUploadDirectory(PipeRoot pr)
    {
        return getExperimentDirectory(pr, DIR_NAME_UPLOAD);
    }

    public static File getMoveDirectory(PipeRoot pr)
    {
        return getExperimentDirectory(pr, DIR_NAME_MOVE);
    }

    private static File getExperimentDirectory(PipeRoot pr, String name)
    {
        File systemDir = pr.ensureSystemDirectory();
        return new File(new File(systemDir, DIR_NAME_EXPERIMENT), name);
    }

    private static File getExperimentDirectory(File systemDir, String name)
    {
        return new File(new File(systemDir, DIR_NAME_EXPERIMENT), name);
    }

    public ExperimentPipelineProvider(Module owningModule)
    {
        super(NAME, owningModule);
    }

    public void initSystemDirectory(File rootDir, File systemDir)
    {
        locateSystemDir(systemDir, DIR_NAME_MOVE);
        locateSystemDir(systemDir, DIR_NAME_UPLOAD);
    }

    public void locateSystemDir(File systemDir, String name)
    {
        File f = new File(systemDir, name);
        if (f.exists())
            f.renameTo(getExperimentDirectory(systemDir, name));
    }

    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory)
    {
        if (!context.getContainer().hasPermission(context.getUser(), InsertPermission.class))
        {
            return;
        }

        addFileActions(ExperimentController.ImportXarFileAction.class, "Import Experiment",
                directory, directory.listFiles(new XarFilenameFilter()));
    }

    private static class XarFilenameFilter extends FileEntryFilter
    {
        public boolean accept(File f)
        {
            String lowerCase = f.getName().toLowerCase();
            return lowerCase.endsWith(".xar.xml") ||
                   lowerCase.endsWith(".xar");
        }
    }
}
