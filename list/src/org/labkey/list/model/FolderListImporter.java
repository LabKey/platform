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

package org.labkey.list.model;

import org.apache.log4j.Logger;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/*
* User: adam
* Date: Aug 27, 2009
* Time: 2:12:01 PM
*/
public class FolderListImporter implements FolderImporter<FolderDocument.Folder>
{
    public String getDescription()
    {
        return "lists";
    }

    public void process(PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
    {
        File listsDir = ctx.getDir("lists");

        if (null != listsDir)
        {
            job.setStatus("IMPORT " + getDescription());
            ctx.getLogger().info("Loading " + getDescription());

            ListImporter importer = new ListImporter();
            Logger log = ctx.getLogger();
            List<String> errors = new LinkedList<String>();
            importer.process(listsDir, ctx.getContainer(), ctx.getUser(), errors, log);

            for (String error : errors)
                log.error(error);

            ctx.getLogger().info("Done importing " + getDescription());
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
            return new FolderListImporter();
        }

        @Override
        public boolean isFinalImporter()
        {
            return false;
        }
    }
}