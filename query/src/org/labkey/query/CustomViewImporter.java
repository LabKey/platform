/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
package org.labkey.query;

import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.query.QueryService;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 2:33:52 PM
 */
public class CustomViewImporter implements FolderImporter<FolderDocument.Folder>
{
    public String getDescription()
    {
        return "custom views";
    }

    public void process(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws IOException, SQLException, ImportException, XmlValidationException
    {
        File viewDir = ctx.getDir("views");

        if (null != viewDir)
        {
            int count = QueryService.get().importCustomViews(ctx.getUser(), ctx.getContainer(), viewDir);

            ctx.getLogger().info(count + " custom view" + (1 == count ? "" : "s") + " imported");
        }
    }

    public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
    {
        //nothing for now
        return null;        
    }

    public static class Factory implements FolderImporterFactory
    {
        public FolderImporter create()
        {
            return new CustomViewImporter();
        }

        @Override
        public boolean isFinalImporter()
        {
            return false;
        }
    }
}