/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * User: klum
 * Date: Dec 10, 2009
*/
public class FileSystemAttachmentParent implements AttachmentDirectory
{
    private static final Logger LOG = Logger.getLogger(FileSystemAttachmentParent.class);

    private Container c;
    private String entityId;
    private String path;
    private String name;
    private boolean relative;
    private FileContentServiceImpl.ContentType _contentType;

    public FileSystemAttachmentParent()
    {
        //For use by auto-construction schemes...
    }

    FileSystemAttachmentParent(Container c, FileContentServiceImpl.ContentType contentType)
    {
        this.c = c;
        this.entityId = c.getId();
        _contentType = contentType;
    }

    public String getEntityId()
    {
        //Just use container id if no path
        return (null == entityId && null == path) ? c.getId() : entityId;
    }

    public Container getContainer()
    {
        return c;
    }

    public void setContainer(Container c)
    {
        this.c = c;
    }

    public String getContainerId()
    {
        return c.getId();
    }

    public File getFileSystemDirectory() throws MissingRootDirectoryException
    {
        Path path = getFileSystemDirectoryPath();
        FileContentServiceImpl.throwIfPathNotFile(path);
        return path.toFile();
    }

    public Path getFileSystemDirectoryPath() throws MissingRootDirectoryException
    {
        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        if (null == svc)
            throw new IllegalStateException("FileContentService not found.");

        try
        {
            Path dir;
            if (null == path)
            {
                dir = ((FileContentServiceImpl) svc).getMappedDirectory(c, false);
            }
            else if (isRelative())
            {
                Path mappedDir = ((FileContentServiceImpl) svc).getMappedDirectory(c, false);
                dir = mappedDir.resolve(path);
            }
            else
            {
                dir = FileUtil.stringToPath(getContainer(), path);
            }

            if (_contentType != null && !svc.isCloudRoot(c))    // don't need @files in cloud
            {
                Path root = dir.resolve(svc.getFolderName(_contentType));
                if (!Files.exists(root))
                    Files.createDirectories(root);
                return root;
            }
            return dir;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e.getMessage());
        }
    }

    public String getLabel()
    {
        return name;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    public void setEntityid(String entityid)
    {
        this.entityId = entityid;
    }

    public boolean isRelative()
    {
        return relative;
    }

    public void setRelative(boolean relative)
    {
        this.relative = relative;
    }

    public void addAttachment(User user, AttachmentFile file) throws IOException
    {
        Path fileLocation = getFileSystemDirectoryPath();
        InputStream is = file.openInputStream();
        Path saveFile = fileLocation.resolve(file.getFilename());
        try
        {
            Files.copy(is, saveFile);
            FileContentServiceImpl.logFileAction(fileLocation, file.getFilename(), FileContentServiceImpl.FileAction.UPLOAD, user);
        }
        finally
        {
            file.closeInputStream();
        }
    }

    public void deleteAttachment(User user, @Nullable String name)
    {
        try
        {
            Path parentDir = getFileSystemDirectoryPath();
            if (parentDir != null && Files.exists(parentDir))
            {
                if (name != null)           // a named resource
                {
                    Path attachmentFile = parentDir.resolve(name);
                    if (Files.exists(attachmentFile))
                    {
                        try
                        {
                            if (Files.deleteIfExists(attachmentFile))
                                FileContentServiceImpl.logFileAction(parentDir, name, FileContentServiceImpl.FileAction.DELETE, user);
                        }
                        catch (IOException e)
                        {
                            LOG.warn(e.getMessage());
                        }
                    }
                }
                else                        // delete the entire folder (and subfolders)
                {
                    try (DirectoryStream<Path> paths = Files.newDirectoryStream(parentDir))
                    {
                        paths.forEach(attachmentFile -> {
                            String fileName = FileUtil.getFileName(attachmentFile);
                            if (!Files.isDirectory(attachmentFile) && !fileName.startsWith(".") && Files.exists(attachmentFile))
                            {
                                try
                                {
                                    if (Files.deleteIfExists(attachmentFile))
                                    {
                                        FileContentServiceImpl.logFileAction(parentDir, fileName, FileContentServiceImpl.FileAction.DELETE, user);
                                        AttachmentService.get().addAuditEvent(user, this, fileName, "The attachment: " + fileName + " was deleted");
                                    }
                                }
                                catch (IOException e)
                                {
                                    LOG.warn(e.getMessage());
                                }
                            }

                        });
                    }
                }
            }
        }
        catch (IOException e)
        {
            LOG.warn(e.getMessage());
        }
    }

    @Override
    public @NotNull AttachmentType getAttachmentType()
    {
        return FileSystemAttachmentType.get();
    }
}
