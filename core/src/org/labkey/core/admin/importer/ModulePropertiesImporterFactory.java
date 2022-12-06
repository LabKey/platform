/*
 * Copyright (c) 2014-2018 LabKey Corporation
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
package org.labkey.core.admin.importer;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportException;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.ModulePropertiesType;
import org.labkey.folder.xml.ModulePropertyType;

import java.util.Collection;
import java.util.Collections;

/**
 * User: vsharma
 * Date: 5/21/14
 * Time: 3:13 PM
 */
public class ModulePropertiesImporterFactory extends AbstractFolderImportFactory
{
    @Override
    public FolderImporter create()
    {
        return new ModulePropertiesImporter();
    }

    public static class ModulePropertiesImporter implements FolderImporter
    {
        @Override
        public String getDataType()
        {
            return FolderArchiveDataTypes.CONTAINER_SPECIFIC_MODULE_PROPERTIES;
        }

        @Override
        public String getDescription()
        {
            return getDataType().toLowerCase();
        }

        @Override
        public void process(PipelineJob job, FolderImportContext ctx, VirtualFile root) throws Exception
        {
            if (isValidForImportArchive(ctx))
            {
                ModulePropertiesType modulePropsType = ctx.getXml().getModuleProperties();

                if (null != job)
                    job.setStatus("IMPORT " + getDescription());
                ctx.getLogger().info("Loading " + getDescription());

                for(ModulePropertyType modulePropType: modulePropsType.getModulePropertyArray())
                {
                    Module module = ModuleLoader.getInstance().getModule(modulePropType.getModuleName());
                    if (module == null)
                    {
                        ctx.getLogger().info("Module '" + modulePropType.getModuleName() + "' not deployed, skipping import of property '" + modulePropType.getPropertyName() + "'");
                    }
                    else
                    {
                        ModuleProperty property = module.getModuleProperties().get(modulePropType.getPropertyName());
                        if (property != null)
                        {
                            property.saveValue(null, ctx.getContainer(), modulePropType.getValue());
                        }
                    }
                }

                ctx.getLogger().info("Done importing " + getDescription());
            }
        }

        @Override
        public boolean isValidForImportArchive(FolderImportContext ctx) throws ImportException
        {
            return ctx.getXml() != null && ctx.getXml().getModuleProperties() != null;
        }
    }
}
