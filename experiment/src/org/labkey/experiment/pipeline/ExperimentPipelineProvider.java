/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.view.ViewContext;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.labkey.vfs.FileLike;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

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

    public static FileLike getMoveDirectory(PipeRoot pr)
    {
        return getExperimentDirectory(pr.ensureSystemDirectory(), DIR_NAME_MOVE);
    }

    private static FileLike getExperimentDirectory(FileLike systemDir, String name)
    {
        return systemDir.resolveChild(DIR_NAME_EXPERIMENT).resolveChild(name);
    }

    public ExperimentPipelineProvider(Module owningModule)
    {
        super(NAME, owningModule);
        setShowActionsIfModuleInactive(true);
    }

    @Override
    public void initSystemDirectory(FileLike rootDir, FileLike systemDir)
    {
        locateSystemDir(systemDir, DIR_NAME_MOVE);
        locateSystemDir(systemDir, DIR_NAME_UPLOAD);
    }

    public void locateSystemDir(FileLike systemDir, String name)
    {
        FileLike path = systemDir.resolveChild(name);
        if (path.exists())
        {
            try
            {
                FileLike dest = getExperimentDirectory(systemDir, name).resolveChild(path.getName());
                path.move(dest);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
        if (!context.getContainer().hasPermission(context.getUser(), InsertPermission.class))
        {
            return;
        }

        String actionId = createActionId(ExperimentController.ImportXarFileAction.class, "Import Experiment");
        addAction(actionId, ExperimentController.ImportXarFileAction.class, "Import Experiment",
                directory, directory.listPaths(new XarFilenameFilter()), true, true, includeAll);
    }

    private static class XarFilenameFilter extends FileEntryFilter
    {
        @Override
        public boolean accept(Path p)
        {
            return accept(p.getFileName().toString().toLowerCase());
        }

        private boolean accept(String lowerCase)
        {
            return lowerCase.endsWith(".xar.xml") ||
                    lowerCase.endsWith(".xar");
        }

        @Override
        public boolean accept(File f)
        {
            return accept(f.getName().toLowerCase());
        }
    }

    @Override
    public boolean supportsCloud()
    {
        return true;
    }
}
