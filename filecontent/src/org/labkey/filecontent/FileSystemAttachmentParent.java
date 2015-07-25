/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * User: klum
 * Date: Dec 10, 2009
*/
public class FileSystemAttachmentParent implements AttachmentDirectory
{
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

    @Override
    public String getDownloadURL(ViewContext context, String name)
    {
        return null;
    }

    public File getFileSystemDirectory() throws MissingRootDirectoryException
    {
        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        File dir;

        if (null == path)
            dir = ((FileContentServiceImpl) svc).getMappedDirectory(c, false);
        else if (isRelative())
        {
            File mappedDir = ((FileContentServiceImpl) svc).getMappedDirectory(c, false);
            dir = new File(mappedDir, path);
        }
        else
            dir = new File(path);

        if (_contentType != null)
        {
            File root = new File(dir, svc.getFolderName(_contentType));
            if (!root.exists())
                root.mkdirs();
            return root;
        }
        return dir;
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
        File fileLocation = getFileSystemDirectory();
        FileOutputStream fos = null;
        InputStream is = file.openInputStream();
        try
        {
            File saveFile = new File(fileLocation, file.getFilename());
            fos = new FileOutputStream(saveFile);
            FileUtil.copyData(is, fos);
            FileContentServiceImpl.logFileAction(fileLocation, file.getFilename(), FileContentServiceImpl.FileAction.UPLOAD, user);
        }
        finally
        {
            IOUtils.closeQuietly(fos);
            file.closeInputStream();
        }
    }

    public void deleteAttachment(User user, @Nullable String name)
    {
        try
        {
            File parentDir = getFileSystemDirectory();
            if (parentDir != null && parentDir.exists())
            {
                if (name != null)           // a named resource
                {
                    File attachmentFile = new File(parentDir, name);
                    if (attachmentFile.exists())
                    {
                        if (attachmentFile.delete())
                        {
                            FileContentServiceImpl.logFileAction(parentDir, name, FileContentServiceImpl.FileAction.DELETE, user);
                        }
                    }
                }
                else                        // delete the entire folder (and subfolders)
                {
                    File[] files = parentDir.listFiles();
                    if (files != null)
                    {
                        for (File attachmentFile : files)
                        {
                            if (!attachmentFile.isDirectory() && !attachmentFile.getName().startsWith(".") && attachmentFile.exists())
                            {
                                if (attachmentFile.delete())
                                {
                                    FileContentServiceImpl.logFileAction(parentDir, attachmentFile.getName(), FileContentServiceImpl.FileAction.DELETE, user);
                                    AttachmentService.get().addAuditEvent(user, this, attachmentFile.getName(), "The attachment: " + attachmentFile.getName() + " was deleted");
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (MissingRootDirectoryException e)
        {
            FileContentServiceImpl._log.warn(e.getMessage());
        }
    }

    @Override
    public SecurityPolicy getSecurityPolicy()
    {
        return null;
    }
}
