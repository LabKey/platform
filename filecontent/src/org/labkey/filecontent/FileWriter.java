/*
 * Copyright (c) 2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Writes the content of the webdav file root to the archive.
 * Created by Josh on 11/1/2016.
 */
public class FileWriter extends BaseFolderWriter
{
    public static final String DIR_NAME = "files";

    public static class Factory implements FolderWriterFactory
    {
        @Override
        public FolderWriter create()
        {
            return new FileWriter();
        }
    }

    @Override
    public String getDataType()
    {
        return FolderArchiveDataTypes.FILES;
    }

    @Override
    public boolean selectedByDefault(AbstractFolderContext.ExportType type)
    {
        // Files could be very large, so make them opt-in
        return false;
    }

    @Override
    public void write(Container container, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
    {
        WebdavResource resource = getFilesDirectory(container);
        if (resource != null)
        {
            VirtualFile virtualRoot = vf.getDir(DIR_NAME);

            // In-line first level of recursion to be able to exclude the "./export" directory, as we may be writing into it right now
            // also exclude the "./cache" and "./unzip" directories as those are LabKey created as well
            List<String> dirsToExclude = Arrays.asList(PipelineService.EXPORT_DIR, PipelineService.UNZIP_DIR, PipelineService.CACHE_DIR);

            for (WebdavResource child : resource.list())
            {
                if (child.isCollection())
                {
                    if (dirsToExclude.stream().noneMatch(child.getName()::equalsIgnoreCase))
                    {
                        virtualRoot.getDir(child.getName()).saveWebdavTree(child, ctx.getUser());
                    }
                }
                else
                {
                    try (InputStream inputStream = child.getInputStream(ctx.getUser()); OutputStream outputStream = virtualRoot.getOutputStream(child.getName()))
                    {
                        if (inputStream != null)
                            FileUtil.copyData(inputStream, outputStream);
                    }
                }
            }
        }
    }

    private WebdavResource getFilesDirectory(Container container)
    {
        WebdavService webdavService = ServiceRegistry.get().getService(WebdavService.class);
        FileContentService fileContentService = FileContentService.get();
        if (null == webdavService || null == fileContentService)
            return null;

        String resourcePath;
        if (fileContentService.isCloudRoot(container))
        {
            String fileRootPath = FileRootManager.get().getFileRoot(container).getPath();
            if (StringUtils.startsWith(fileRootPath, "/"))
                fileRootPath = fileRootPath.substring(1);
            resourcePath = new Path(WebdavService.getServletPath()).append(container.getParsedPath()).append(fileRootPath).toString();
        }
        else
        {
            resourcePath = new Path(WebdavService.getServletPath()).append(container.getParsedPath()).append(FileContentService.FILES_LINK).toString();
        }
        return webdavService.lookup(resourcePath);
    }
}
