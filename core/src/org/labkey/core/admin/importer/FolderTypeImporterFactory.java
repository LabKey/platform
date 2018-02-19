/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.data.Container;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.settings.WriteableFolderLookAndFeelProperties;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * User: cnathe
 * Date: Apr 10, 2012
 */
public class FolderTypeImporterFactory extends AbstractFolderImportFactory
{
    @Override
    public FolderImporter create()
    {
        return new FolderTypeImporter();
    }

    public class FolderTypeImporter implements FolderImporter<FolderDocument.Folder>
    {
        @Override
        public String getDataType()
        {
            return FolderArchiveDataTypes.FOLDER_TYPE_AND_ACTIVE_MODULES;
        }

        @Override
        public String getDescription()
        {
            return "folder properties (folder type, settings and active modules)";
        }

        @Override
        public void process(PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            Container c = ctx.getContainer();
            FolderDocument.Folder folderXml = ctx.getXml();

            if (folderXml.isSetDefaultDateFormat())
            {
                try
                {
                    WriteableFolderLookAndFeelProperties.saveDefaultDateFormat(c, folderXml.getDefaultDateFormat());
                }
                catch (IllegalArgumentException e)
                {
                    ctx.getLogger().warn("Illegal default date format specified: " + e.getMessage());
                }
            }

            if (folderXml.isSetDefaultDateTimeFormat())
            {
                try
                {
                    WriteableFolderLookAndFeelProperties.saveDefaultDateTimeFormat(c, folderXml.getDefaultDateTimeFormat());
                }
                catch (IllegalArgumentException e)
                {
                    ctx.getLogger().warn("Illegal default date-time format specified: " + e.getMessage());
                }
            }

            if (folderXml.isSetRestrictedColumnsEnabled())
            {
                WriteableFolderLookAndFeelProperties.saveRestrictedColumnsEnabled(c, folderXml.getRestrictedColumnsEnabled());
            }

            if (folderXml.isSetDefaultNumberFormat())
            {
                try
                {
                    WriteableFolderLookAndFeelProperties.saveDefaultNumberFormat(c, folderXml.getDefaultNumberFormat());
                }
                catch (IllegalArgumentException e)
                {
                    ctx.getLogger().warn("Illegal default number format specified: " + e.getMessage());
                }
            }

            if (folderXml.isSetFolderType())
            {
                if (null != job)
                    job.setStatus("IMPORT " + getDescription());
                ctx.getLogger().info("Loading " + getDescription());

                org.labkey.folder.xml.FolderType folderTypeXml = folderXml.getFolderType();
                FolderType folderType = FolderTypeManager.get().getFolderType(folderTypeXml.getName());

                org.labkey.folder.xml.FolderType.Modules modulesXml = folderTypeXml.getModules();
                Set<Module> activeModules = new HashSet<>();
                for (String moduleName : modulesXml.getModuleNameArray())
                {
                    Module module = ModuleLoader.getInstance().getModule(moduleName);
                    if (null != module)
                        activeModules.add(module);
                }

                if (null != folderType)
                {
                    // It's sorta BrandNew, but not really; say it's not and SubImporter will handle container tabs correctly
                    BindException errors = new BindException(new Object(), "dummy");
                    c.setFolderType(folderType, activeModules, ctx.getUser(), errors);
                    if (errors.hasErrors())
                    {
                        for (Object error : errors.getAllErrors())
                        {
                            if (error instanceof ObjectError)
                                ctx.getLogger().error(((ObjectError)error).getDefaultMessage());
                            else
                                ctx.getLogger().error("Unknown error attempting to set folder type or enable modules.");
                        }
                    }
                }
                else
                {
                    ctx.getLogger().warn("Unknown folder type: '" + folderTypeXml.getName() + "'. Folder type and active modules not set.");
                }

                Module defaultModule = ModuleLoader.getInstance().getModule(folderTypeXml.getDefaultModule());
                if (null != defaultModule)
                {
                    c.setDefaultModule(defaultModule);
                }
                
                ctx.getLogger().info("Done importing " + getDescription());
            }
        }

        @NotNull
        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            return Collections.emptyList();
        }

        @Override
        public boolean isValidForImportArchive(ImportContext<FolderDocument.Folder> ctx) throws ImportException
        {
            return ctx.getXml() != null;
        }
    }
}
