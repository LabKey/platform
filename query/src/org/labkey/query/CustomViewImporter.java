/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 2:33:52 PM
 */
public class CustomViewImporter implements FolderImporter
{
    public String getDataType()
    {
        return FolderArchiveDataTypes.GRID_VIEWS;
    }

    public String getDescription()
    {
        return getDataType().toLowerCase();
    }

    public void process(PipelineJob job, ImportContext ctx, VirtualFile root) throws IOException, SQLException, ImportException, XmlValidationException
    {
        if (isValidForImportArchive(ctx))
        {
            VirtualFile viewDir = ctx.getDir("views");

            if (null != job)
                job.setStatus("IMPORT " + getDescription());
            ctx.getLogger().info("Loading " + getDescription());

            int count = QueryService.get().importCustomViews(ctx.getUser(), ctx.getContainer(), viewDir);
            
            ctx.getLogger().info(count + " custom view" + (1 == count ? "" : "s") + " imported");
            ctx.getLogger().info("Done importing " + getDescription());
        }
    }

    @NotNull
    public Collection<PipelineJobWarning> postProcess(ImportContext ctx, VirtualFile root) throws Exception
    {
        return Collections.emptyList();
    }

    @Override
    public boolean isValidForImportArchive(ImportContext ctx) throws ImportException
    {
        return ctx.getDir("views") != null;
    }

    public static class Factory extends AbstractFolderImportFactory
    {
        public FolderImporter create()
        {
            return new CustomViewImporter();
        }
    }
}