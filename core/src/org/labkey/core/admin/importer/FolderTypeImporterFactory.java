/*
 * Copyright (c) 2012-2026 LabKey Corporation
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

import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportException;
import org.labkey.api.data.Container;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.settings.WriteableFolderLookAndFeelProperties;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument.Folder;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class FolderTypeImporterFactory extends AbstractFolderImportFactory
{
    @Override
    public FolderImporter create()
    {
        return new FolderTypeImporter();
    }

    public static class FolderTypeImporter implements FolderImporter
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
        public void process(PipelineJob job, FolderImportContext ctx, VirtualFile root) throws Exception
        {
            Container c = ctx.getContainer();
            Folder folderXml = ctx.getXml();
            ctx.getLogger().debug("[{}] Importing folder properties from: {}", c.getPath(), root.getLocation());

            if (folderXml.isSetDefaultDateFormat())
            {
                try
                {
                    ctx.getLogger().debug("[{}] Default date format: {}", c.getPath(), folderXml.getDefaultDateFormat());
                    WriteableFolderLookAndFeelProperties.saveDefaultDateFormat(c, folderXml.getDefaultDateFormat());
                }
                catch (IllegalArgumentException e)
                {
                    ctx.getLogger().warn("Illegal default date format specified: {}", e.getMessage());
                }
            }

            if (folderXml.isSetDefaultDateTimeFormat())
            {
                try
                {
                    ctx.getLogger().debug("[{}] Default date-time format: {}", c.getPath(), folderXml.getDefaultDateTimeFormat());
                    WriteableFolderLookAndFeelProperties.saveDefaultDateTimeFormat(c, folderXml.getDefaultDateTimeFormat());
                }
                catch (IllegalArgumentException e)
                {
                    ctx.getLogger().warn("Illegal default date-time format specified: {}", e.getMessage());
                }
            }

            if (folderXml.isSetDefaultTimeFormat())
            {
                try
                {
                    ctx.getLogger().debug("[{}] Default time format: {}", c.getPath(), folderXml.getDefaultTimeFormat());
                    WriteableFolderLookAndFeelProperties.saveDefaultTimeFormat(c, folderXml.getDefaultTimeFormat());
                }
                catch (IllegalArgumentException e)
                {
                    ctx.getLogger().warn("Illegal default time format specified: {}", e.getMessage());
                }
            }

            if (folderXml.isSetRestrictedColumnsEnabled())
            {
                ctx.getLogger().debug("[{}] Restricted columns enabled: {}", c.getPath(), folderXml.getRestrictedColumnsEnabled());
                WriteableFolderLookAndFeelProperties.saveRestrictedColumnsEnabled(c, folderXml.getRestrictedColumnsEnabled());
            }

            if (folderXml.isSetDefaultNumberFormat())
            {
                try
                {
                    ctx.getLogger().debug("[{}] Default number format: {}", c.getPath(), folderXml.getDefaultNumberFormat());
                    WriteableFolderLookAndFeelProperties.saveDefaultNumberFormat(c, folderXml.getDefaultNumberFormat());
                }
                catch (IllegalArgumentException e)
                {
                    ctx.getLogger().warn("Illegal default number format specified: {}", e.getMessage());
                }
            }

            // For now, fail with a clear error if extra date/time parsing formats are specified.
            // TODO: Remove these from the XSD.
            if (folderXml.isSetExtraDateParsingPattern())
            {
                ctx.getLogger().error("[{}] Extra date parsing format is not longer supported", c.getPath());
            }

            if (folderXml.isSetExtraDateTimeParsingPattern())
            {
                ctx.getLogger().error("[{}] Extra date-time parsing format is not longer supported", c.getPath());
            }

            if (folderXml.isSetExtraTimeParsingPattern())
            {
                ctx.getLogger().error("[{}] Extra time parsing format is not longer supported", c.getPath());
            }

            if (folderXml.isSetFolderType())
            {
                if (null != job)
                    job.setStatus("IMPORT " + getDescription());
                ctx.getLogger().info("Loading {}", getDescription());

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
                    ctx.getLogger().debug("[{}] Folder type: {}", c.getPath(), folderType.getName());
                    ctx.getLogger().debug("[{}] Active modules: {}", c.getPath(), activeModules.stream().map(Module::getName).collect(Collectors.joining(", ")));
                    // It's sorta BrandNew, but not really; say it's not and SubImporter will handle container tabs correctly
                    BindException errors = new BindException(new Object(), "dummy");
                    c.setFolderType(folderType, ctx.getUser(), errors, activeModules);
                    for (ObjectError error : errors.getAllErrors())
                    {
                        ctx.getLogger().error(error.getDefaultMessage());
                    }
                }
                else
                {
                    ctx.getLogger().warn("Unknown folder type: '{}'. Folder type and active modules not set.", folderTypeXml.getName());
                }

                Module defaultModule = ModuleLoader.getInstance().getModule(folderTypeXml.getDefaultModule());
                if (null != defaultModule)
                {
                    c.setDefaultModule(defaultModule);
                }

                ctx.getLogger().info("Done importing {}", getDescription());
            }
        }

        @Override
        public boolean isValidForImportArchive(FolderImportContext ctx) throws ImportException
        {
            return ctx.getXml() != null;
        }
    }
}
