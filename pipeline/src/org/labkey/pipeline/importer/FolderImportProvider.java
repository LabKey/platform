/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.pipeline.importer;

import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.pipeline.PipelineController;

import java.nio.file.DirectoryStream;
import java.nio.file.Path;

/**
 * Recognizes folder archives and attaches import options for use in the file browser.
 * User: cnathe
 * Date: Jan 19, 2012
 */
public class FolderImportProvider extends PipelineProvider
{
    public FolderImportProvider(Module owningModule)
    {
        super("FolderImport", owningModule);
        setShowActionsIfModuleInactive(true);
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
        // Only admins can import folders
        if (!context.getContainer().hasPermission(context.getUser(), AdminPermission.class))
            return;

        String actionId = createActionId(PipelineController.ImportFolderFromPipelineAction.class, null);
        addAction(
                actionId,
                PipelineController.ImportFolderFromPipelineAction.class,
                "Import Folder",
                directory,
                directory.listPaths(new FolderImportFilter()),
                false,
                false,
                includeAll
        );
    }

    public static String generateLogFilename(String prefix)
    {
        return FileUtil.makeFileNameWithTimestamp(prefix);
    }

    @Override
    public boolean supportsCloud()
    {
        return true;
    }

    private static class FolderImportFilter implements DirectoryStream.Filter<Path>
    {
        @Override
        public boolean accept(Path path)
        {
            return path.getFileName().toString().endsWith("folder.xml") || path.getFileName().toString().endsWith(".folder.zip");
        }
    }
}
