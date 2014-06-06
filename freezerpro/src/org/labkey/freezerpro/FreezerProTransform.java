/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
import org.labkey.api.data.PropertyManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.Encryption;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.SpecimenTransform;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.freezerpro.export.FreezerProExport;

import java.io.File;
import java.util.Map;

/**
 * User: klum
 * Date: 11/13/13
 */
public class FreezerProTransform implements SpecimenTransform
{
    public static final String NAME = "FreezerPro";

    @Override
    public String getName()
    {
        return NAME;
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
        if (c.hasPermission(user, AdminPermission.class))
            return new ActionURL(FreezerProController.ConfigureAction.class, c);
        return null;
    }

    @Override
    public ExternalImportConfig getExternalImportConfig(Container c, User user) throws ValidationException
    {
        if (Encryption.isMasterEncryptionPassPhraseSpecified())
        {
            Map<String, String> props = PropertyManager.getEncryptedStore().getProperties(c, FreezerProController.FREEZER_PRO_PROPERTIES);
            FreezerProConfig config = new FreezerProConfig();

            String url = props.get(FreezerProConfig.Options.url.name());
            String username = props.get(FreezerProConfig.Options.user.name());
            String password = props.get(FreezerProConfig.Options.password.name());

            if (url == null || username == null || password == null)
                throw new ValidationException("Server URL, username and password must all be configured before the FreezerPro export can be run.");

            config.setBaseServerUrl(url);
            config.setUsername(username);
            config.setPassword(password);
            config.setMetadata(props.get(FreezerProConfig.Options.metadata.name()));
            config.setImportUserFields(true);

            return config;
        }
        else
        {
            throw new ValidationException("Unable to save or retrieve configuration information, MasterEncryptionKey has not been specified in labkey.xml.");
        }
    }

    @Override
    public void importFromExternalSource(@Nullable PipelineJob job, ExternalImportConfig importConfig, File inputArchive) throws PipelineJobException
    {
        if (importConfig instanceof FreezerProConfig)
        {
            FreezerProExport export = new FreezerProExport((FreezerProConfig)importConfig, job, inputArchive);
            export.exportRepository();
        }
    }
}
