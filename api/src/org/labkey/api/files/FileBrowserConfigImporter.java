/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
package org.labkey.api.files;

import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.PipelineOptionsDocument;
import org.labkey.folder.xml.FolderDocument;

import static org.labkey.api.admin.FolderArchiveDataTypes.FILE_BROWSER_SETTINGS;

public class FileBrowserConfigImporter implements FolderImporter
{
    @Override
    public String getDataType()
    {
        return FILE_BROWSER_SETTINGS;
    }

    @Override
    public String getDescription()
    {
        return "File Browser Admin Configurations";
    }

    @Override
    public void process(@Nullable PipelineJob job, FolderImportContext ctx, VirtualFile root) throws Exception
    {
        if (!isValidForImportArchive(ctx))
            return;

        ctx.getLogger().info("Loading File Browser config");

        PipelineOptionsDocument doc = getSettingsFile(ctx, root);
        if (doc != null)
        {
            Container container = ctx.getContainer();

            FileContentService fileContentService = FileContentService.get();
            if (fileContentService != null)
                fileContentService.setAdminOptions(container, FilesAdminOptions.serialize(doc, container));
        }

        ctx.getLogger().info("Done importing File Browser config");
    }

    @Nullable
    private PipelineOptionsDocument getSettingsFile(FolderImportContext ctx, VirtualFile root) throws Exception
    {
        FolderDocument.Folder.FileBrowserConfig configXml = ctx.getXml().getFileBrowserConfig();

        if (configXml != null)
        {
            String fileName = configXml.getFile();

            if (fileName != null)
            {
                XmlObject doc = root.getXmlBean(fileName);
                if (doc instanceof PipelineOptionsDocument)
                    return (PipelineOptionsDocument)doc;
            }
        }
        return null;
    }

    public static class Factory extends AbstractFolderImportFactory
    {
        @Override
        public FolderImporter create()
        {
            return new FileBrowserConfigImporter();
        }

        @Override
        public int getPriority()
        {
            // after PageImporter
            return 85;
        }
    }
}
