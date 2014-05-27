/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.freezerpro;

import com.drew.lang.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.SpecimenTransform;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ActionURL;
import org.labkey.freezerpro.export.FreezerProExport;

import java.io.File;

/**
 * User: klum
 * Date: 11/13/13
 */
public class FreezerProTransform implements SpecimenTransform
{
    @Override
    public String getName()
    {
        return "FreezerPro";
    }

    @Override
    public boolean isEnabled(Container container)
    {
        return container.getActiveModules().contains(ModuleLoader.getInstance().getModule(FreezerProModule.class));
    }

    @Override
    public FileType getFileType()
    {
        return FreezerProTransformTask.FREEZER_PRO_FILE_TYPE;
    }

    @Override
    public void transform(@Nullable PipelineJob job, File input, File outputArchive) throws PipelineJobException
    {
        FreezerProTransformTask task = new FreezerProTransformTask(job);
        task.transform(input, outputArchive);
    }

    @Override
    public void postTransform(@Nullable PipelineJob job, File input, File outputArchive) throws PipelineJobException
    {
        // noop
    }

    @Override
    public ActionURL getManageAction(Container c, User user)
    {
        // uncomment once integration with freezerPro api's is implemented
/*
        if (c.hasPermission(user, AdminPermission.class))
            return new ActionURL(FreezerProController.ConfigureAction.class, c);
*/
        return null;
    }

    @Override
    public ExternalImportConfig getExternalImportConfig(Container c, User user)
    {
        // TODO : wire up persisted freezerPro config settings
        FreezerProController.FreezerProConfig config = new FreezerProController.FreezerProConfig();

        config.setImportUserFields(true);

        return config;
    }

    @Override
    public void importFromExternalSource(@Nullable PipelineJob job, ExternalImportConfig importConfig, File inputArchive) throws PipelineJobException
    {
        if (importConfig instanceof FreezerProController.FreezerProConfig)
        {
            FreezerProExport export = new FreezerProExport((FreezerProController.FreezerProConfig)importConfig, job, inputArchive);
            export.exportRepository();
        }
    }
}
