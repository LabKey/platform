/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.specimen.pipeline;

import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.specimen.importer.AbstractSpecimenTask;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.SpecimenTransform;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.ViewContext;
import org.labkey.specimen.actions.SpecimenController.ImportSpecimenDataAction;

import java.io.File;

public class SpecimenPipeline extends PipelineProvider
{
    public SpecimenPipeline(Module owningModule)
    {
        super("Specimen", owningModule);
    }

    @Override
    public void updateFileProperties(final ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
        if (
            !context.getContainer().hasPermission(context.getUser(), InsertPermission.class) ||
            context.getContainer().isDataspace() || // Cannot import specimens into Dataspace container
            StudyService.get().getStudy(context.getContainer()) == null)
        {
            return;
        }

        File[] files = directory.listFiles(new FileEntryFilter()
        {
            @Override
            public boolean accept(File f)
            {
                if (AbstractSpecimenTask.ARCHIVE_FILE_TYPE.isType(f))
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

        String actionId = createActionId(ImportSpecimenDataAction.class, "Import Specimen Data");
        addAction(actionId, ImportSpecimenDataAction.class, "Import Specimen Data", directory, files, true, false, includeAll);
    }
}
