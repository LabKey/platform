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
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

import java.util.Collection;
import java.util.Collections;

/**
 * User: cnathe
 * Date: Apr 10, 2012
 */
public class SearchSettingsImporterFactory extends AbstractFolderImportFactory
{
    @Override
    public FolderImporter create()
    {
        return new SearchSettingsImporter();
    }

    public class SearchSettingsImporter implements  FolderImporter<FolderDocument.Folder>
    {
        @Override
        public String getDataType()
        {
            return FolderArchiveDataTypes.FULL_TEXT_SEARCH_SETTINGS;
        }

        @Override
        public String getDescription()
        {
            return getDataType().toLowerCase();
        }

        @Override
        public void process(PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            Container c = ctx.getContainer();
            if (isValidForImportArchive(ctx))
            {
                if (null != job)
                    job.setStatus("IMPORT " + getDescription());
                ctx.getLogger().info("Loading " + getDescription());
                ContainerManager.updateSearchable(c, ctx.getXml().getSearchable(), ctx.getUser());
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
            return ctx.getXml() != null && ctx.getXml().isSetSearchable();
        }
    }
}
