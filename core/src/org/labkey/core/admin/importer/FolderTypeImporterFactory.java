/*
 * Copyright (c) 2012-2018 LabKey Corporation
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
import org.labkey.api.admin.FolderImporter;
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
import java.util.stream.Collectors;

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
            ctx.getLogger().debug("[" + c.getPath() + "] Importing folder properties from: " + root.getLocation());

            if (folderXml.isSetDefaultDateFormat())
            {
                try
                {
                    ctx.getLogger().debug("[" + c.getPath() + "] Default date format: " + folderXml.getDefaultDateFormat());
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
                    ctx.getLogger().debug("[" + c.getPath() + "] Default date-time format: " + folderXml.getDefaultDateTimeFormat());
                    WriteableFolderLookAndFeelProperties.saveDefaultDateTimeFormat(c, folderXml.getDefaultDateTimeFormat());
                }
                catch (IllegalArgumentException e)
                {
                    ctx.getLogger().warn("Illegal default date-time format specified: " + e.getMessage());
                }
            }

            if (folderXml.isSetRestrictedColumnsEnabled())
            {
                ctx.getLogger().debug("[" + c.getPath() + "] Restricted columns enabled: " + folderXml.getRestrictedColumnsEnabled());
                WriteableFolderLookAndFeelProperties.saveRestrictedColumnsEnabled(c, folderXml.getRestrictedColumnsEnabled());
            }

            if (folderXml.isSetDefaultNumberFormat())
            {
                try
                {
                    ctx.getLogger().debug("[" + c.getPath() + "] Default number format: " + folderXml.getDefaultNumberFormat());
                    WriteableFolderLookAndFeelProperties.saveDefaultNumberFormat(c, folderXml.getDefaultNumberFormat());
                }
                catch (IllegalArgumentException e)
                {
                    ctx.getLogger().warn("Illegal default number format specified: " + e.getMessage());
                }
            }

            if (folderXml.isSetExtraDateParsingPattern())
            {
                try
                {
                    ctx.getLogger().debug("[" + c.getPath() + "] Extra date parsing format: " + folderXml.getExtraDateParsingPattern());
                    WriteableFolderLookAndFeelProperties.saveExtraDateParsingPattern(c, folderXml.getExtraDateParsingPattern());
                }
                catch (IllegalArgumentException e)
                {
                    ctx.getLogger().warn("Illegal default date format specified: " + e.getMessage());
                }
            }

            if (folderXml.isSetExtraDateTimeParsingPattern())
            {
                try
                {
                    ctx.getLogger().debug("[" + c.getPath() + "] Extra date-time parsing format: " + folderXml.getExtraDateTimeParsingPattern());
                    WriteableFolderLookAndFeelProperties.saveExtraDateTimeParsingPattern(c, folderXml.getExtraDateTimeParsingPattern());
                }
                catch (IllegalArgumentException e)
                {
                    ctx.getLogger().warn("Illegal default date-time format specified: " + e.getMessage());
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
                    ctx.getLogger().debug("[" + c.getPath() + "] Folder type: " + folderType.getName());
                    ctx.getLogger().debug("[" + c.getPath() + "] Active modules: " + activeModules.stream().map(Module::getName).collect(Collectors.joining(", ")));
                    // It's sorta BrandNew, but not really; say it's not and SubImporter will handle container tabs correctly
                    BindException errors = new BindException(new Object(), "dummy");
                    c.setFolderType(folderType, activeModules, ctx.getUser(), errors);
                    for (ObjectError error : errors.getAllErrors())
                    {
                        ctx.getLogger().error(error.getDefaultMessage());
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
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root)
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
