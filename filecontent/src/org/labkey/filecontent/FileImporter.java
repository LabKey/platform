/*
 * Copyright (c) 2017-2018 LabKey Corporation
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
package org.labkey.filecontent;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument.Folder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.labkey.filecontent.FileWriter.DIR_NAME;

/**
 * Imports into the webdav file root for the folder.
 * Created by Josh on 11/1/2016.
 */
public class FileImporter implements FolderImporter
{
    /** Milliseconds between log progress messages */
    public static final long LOG_INTERVAL = TimeUnit.MINUTES.toMillis(2);

    @Override
    public String getDataType()
    {
        return FolderArchiveDataTypes.FILES;
    }

    @Override
    public String getDescription()
    {
        return getDataType().toLowerCase();
    }

    @Override
    public void process(@Nullable PipelineJob job, FolderImportContext ctx, VirtualFile root) throws Exception
    {
        VirtualFile filesVF = root.getDir(DIR_NAME);
        if (filesVF != null)
        {
            FileContentService service = FileContentService.get();
            Path rootFile = service.getFileRootPath(ctx.getContainer(), FileContentService.ContentType.files);
            if (!Files.exists(rootFile))
                Files.createDirectories(rootFile);
            if (Files.isDirectory(rootFile))
            {
                ctx.getLogger().info("Starting to copy files");
                AtomicInteger count = new AtomicInteger();
                copy(filesVF, rootFile, ctx.getLogger(), System.currentTimeMillis() + LOG_INTERVAL, count);

                ctx.getLogger().info("Copied " + count.get() + " files");

                ctx.getLogger().info("Ensuring exp.data rows exist for imported files");
                // Ensure that we have an exp.data row for each file
                ExpDataTable table = ExperimentService.get().createDataTable("data", new ExpSchema(ctx.getUser(), ctx.getContainer()), null);
                service.ensureFileData(table);
            }
        }
    }

    private void copy(VirtualFile virtualFile, Path realPath, Logger log, long nextLogTime, AtomicInteger count) throws IOException
    {
        for (String child : virtualFile.list())
        {
            Path childPath = realPath.resolve(child);
            try (InputStream in = virtualFile.getInputStream(child))
            {
                if (nextLogTime < System.currentTimeMillis())
                {
                    log.info(count.get() + " files copied, now copying " + child);
                    nextLogTime = System.currentTimeMillis() + LOG_INTERVAL;
                }

                Files.copy(in, childPath, StandardCopyOption.REPLACE_EXISTING);
                count.incrementAndGet();

            }
        }

        for (String childDir : virtualFile.listDirs())
        {
            Path realChildDir = realPath.resolve(childDir);
            if (!Files.exists(realChildDir))
                Files.createDirectories(realChildDir);
            if (!Files.isDirectory(realChildDir))
            {
                throw new IOException("Failed to create directory " + realChildDir);        // TODO probably unnecessary
            }
            copy(virtualFile.getDir(childDir), realChildDir, log, nextLogTime, count);
        }
    }

    @NotNull
    @Override
    public Collection<PipelineJobWarning> postProcess(FolderImportContext ctx, VirtualFile root)
    {
        return Collections.emptyList();
    }

    public static class Factory extends AbstractFolderImportFactory
    {
        @Override
        public FolderImporter create()
        {
            return new FileImporter();
        }

        @Override
        public int getPriority()
        {
            return 60;
        }
    }
}
