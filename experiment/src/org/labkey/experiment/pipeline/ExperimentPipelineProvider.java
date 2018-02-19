/*
 * Copyright (c) 2005-2010 LabKey Corporation
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
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.api.module.Module;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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

    public static Path getMoveDirectory(PipeRoot pr)
    {
        return getExperimentDirectory(pr, DIR_NAME_MOVE);
    }

    private static Path getExperimentDirectory(PipeRoot pr, String name)
    {
        Path systemDir = pr.ensureSystemDirectoryPath();
        return systemDir.resolve(DIR_NAME_EXPERIMENT).resolve(name);
    }

    private static Path getExperimentDirectory(Path systemDir, String name)
    {
        return systemDir.resolve(DIR_NAME_EXPERIMENT).resolve(name);
    }

    public ExperimentPipelineProvider(Module owningModule)
    {
        super(NAME, owningModule);
        setShowActionsIfModuleInactive(true);
    }

    public void initSystemDirectory(Path rootDir, Path systemDir)
    {
        locateSystemDir(systemDir, DIR_NAME_MOVE);
        locateSystemDir(systemDir, DIR_NAME_UPLOAD);
    }

    public void locateSystemDir(Path systemDir, String name)
    {
        Path path = systemDir.resolve(name);
        if (Files.exists(path))
        {
            try
            {
                Path dest = getExperimentDirectory(systemDir, name).resolve(FileUtil.getFileName(path));
                Files.move(path, dest);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
        if (!context.getContainer().hasPermission(context.getUser(), InsertPermission.class))
        {
            return;
        }

        String actionId = createActionId(ExperimentController.ImportXarFileAction.class, "Import Experiment");
        addAction(actionId, ExperimentController.ImportXarFileAction.class, "Import Experiment",
                directory, directory.listFiles(new XarFilenameFilter()), true, true, includeAll);
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

    @Override
    public boolean supportsCloud()
    {
        return true;
    }
}
