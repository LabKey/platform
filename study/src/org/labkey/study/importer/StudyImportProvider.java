/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

package org.labkey.study.importer;

import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.view.ViewContext;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.module.Module;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.StudyManager;

import java.io.File;
import java.io.FileFilter;

/*
* User: adam
* Date: Aug 27, 2009
* Time: 9:00:33 PM
*/
public class StudyImportProvider extends PipelineProvider
{
    public StudyImportProvider(Module owningModule)
    {
        super("StudyImport", owningModule);
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
        // Only admins can import/reload studies
        if (!context.getContainer().hasPermission(context.getUser(), AdminPermission.class))
            return;

        if (context.getContainer().isDataspace())
            return;         // Cannot import study into Dataspace container

        String label = (null == StudyManager.getInstance().getStudy(context.getContainer()) ? "Import Study" : "Reload Study");
        String actionId = createActionId(StudyController.ImportStudyFromPipelineAction.class, null);
        addAction(actionId, StudyController.ImportStudyFromPipelineAction.class, label, directory, directory.listFiles(new StudyImportFilter()), false, false, includeAll);
    }

    private static class StudyImportFilter implements FileFilter
    {
        public boolean accept(File file)
        {
            return file.getName().endsWith("study.xml") || file.getName().endsWith(".study.zip") || file.getName().endsWith(".folder.zip");
        }
    }
}
