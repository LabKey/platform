/*
 * Copyright (c) 2009-2018 LabKey Corporation
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
package org.labkey.api.util;

import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.util.logging.LogHelper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Date;

/**
 * User: Matthew
 * Date: Apr 29, 2009
 * Time: 9:29:16 PM
 *
 * Useful when we need to communicate the length of the stream (that is not a FileInputStream e.g.)
 *
 * NOTE: see org.apache.commons.fileupload.FileItem.  Perhaps this should be merged???
 */
public interface FileStream
{
    Logger LOG = LogHelper.getLogger(FileStream.class, "File transfers");

    // See issue 50620
    String STAGE_FILE_UPLOADS = "StageFileUploads";

    /** @return -1 if unknown */
    long getSize() throws IOException;

    @Nullable
    default Date getLastModified() { return null; }

    InputStream openInputStream() throws IOException;

    /** Override to provide a more optimized Channel */
    default ReadableByteChannel getInputChannel() throws IOException
    {
        return Channels.newChannel(openInputStream());
    }

    void closeInputStream() throws IOException;

    default void transferTo(File dest) throws IOException
    {
        transferToImpl(this, dest);
    }

    static void transferToImpl(FileStream s, File dest) throws IOException
    {
        // see CommonsMultipartFile.transferTo()
        if (dest.exists() && !dest.delete())
        {
            throw new IOException("Destination file [" + dest.getAbsolutePath() + "] already exists and could not be deleted");
        }

        long size = s.getSize();
        if (OptionalFeatureService.get().isFeatureEnabled(STAGE_FILE_UPLOADS))
        {
            File tempFile = FileUtil.createTempFile(STAGE_FILE_UPLOADS, ".tmp");
            try
            {
                // Although the FileStream passed here is backed by temp file on disk, the underlying FileChannelImpl
                // is wrapped in a ChannelInputStream due to the way it's returned from Spring and Tomcat's APIs.
                // That means the copy can't use the ultra-efficient file->file channel transfer implementation.
                // Most of the time that doesn't really matter, but for certain NFS mounts, it's horrifically slower.
                // It's way faster to copy to an intermediate temp file that can then serve as a fast transfer source.
                // See issue 50620.
                try (ReadableByteChannel in = s.getInputChannel())
                {
                    FileUtil.copyFile(in, s.getSize(), tempFile);
                }
                try (FileInputStream fIn = new FileInputStream(tempFile);
                    ReadableByteChannel in = fIn.getChannel())
                {
                    FileUtil.copyFile(in, size, dest);
                }
            }
            finally
            {
                if (!tempFile.delete() && tempFile.exists())
                {
                    LOG.warn("Failed to delete temporary file [" + tempFile.getAbsolutePath() + "]");
                }
            }
        }
        else
        {
            try (ReadableByteChannel in = s.getInputChannel())
            {
                FileUtil.copyFile(in, size, dest);
            }
        }
    }


    FileStream EMPTY = new FileStream()
    {
        @Override
        public long getSize()
        {
            return 0;
        }

        @Override
        public InputStream openInputStream()
        {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void closeInputStream()
        {
        }
    };


    class ByteArrayFileStream implements FileStream
    {
        private ByteArrayInputStream in;

        public ByteArrayFileStream(ByteArrayInputStream b)
        {
            in = b;
            MemTracker.getInstance().put(in);
        }

        public ByteArrayFileStream(byte[] buf)
        {
            in = new ByteArrayInputStream(buf);
            MemTracker.getInstance().put(in);
        }

        public ByteArrayFileStream(ByteArrayOutputStream out)
        {
            this(new ByteArrayInputStream(out.toByteArray()));
            MemTracker.getInstance().put(in);
        }

        @Override
        public long getSize()
        {
            return in.available();
        }

        @Override
        public InputStream openInputStream()
        {
            return in;
        }

        @Override
        public void closeInputStream()
        {
            IOUtils.closeQuietly(in);
            MemTracker.getInstance().remove(in);
            in = null;
        }
    }


    class StringFileStream extends ByteArrayFileStream
    {
        public StringFileStream(String s)
        {
            super(toBytes(s));
        }

        static byte[] toBytes(String s)
        {
            return s.getBytes(StringUtilsLabKey.DEFAULT_CHARSET);
        }
    }


    class FileFileStream implements FileStream
    {
        private File file = null;
        boolean deleteOnClose = false;
        private FileInputStream in;

        public FileFileStream(@NotNull File f) throws IOException
        {
            file = f;
        }

        public FileFileStream(@NotNull File f, boolean delete) throws IOException
        {
            file = f;
            deleteOnClose = delete;
        }

        public FileFileStream(@NotNull FileObject fo, boolean delete) throws IOException
        {
            file = fo.getPath().toFile();
            deleteOnClose = delete;
        }

        public FileFileStream(@NotNull FileInputStream fin)
        {
            in = fin;
            MemTracker.getInstance().put(in);
        }

        @Override
        public long getSize() throws IOException
        {
            if (null != file)
                return file.length();
            else
                return in.getChannel().size();
        }

        @Override
        public FileInputStream openInputStream() throws IOException
        {
            if (file != null && in == null)
                in = new FileInputStream(file);
            return in;
        }

        @Override
        public void closeInputStream()
        {
            IOUtils.closeQuietly(in);
            MemTracker.getInstance().remove(in);
            in = null;
            if (deleteOnClose && null != file)
                file.delete();
        }

        @Override
        public void transferTo(File dest) throws IOException
        {
            if (null != file)
                if (file.renameTo(dest))
                    return;
            FileStream.super.transferTo(dest);
        }
    }

    // TODO can we merge FileStream and FileContent???
    class FileContentFileStream implements FileStream
    {
        private final FileContent content;
        InputStream in;

        public FileContentFileStream(FileContent c)
        {
            content = c;
        }

        @Override
        public long getSize() throws IOException
        {
            return content.getSize();
        }

        @Override
        public @Nullable Date getLastModified()
        {
            try
            {
                return new Date(content.getLastModifiedTime());
            }
            catch (FileSystemException x)
            {
                throw UnexpectedException.wrap(x);
            }
        }

        @Override
        public InputStream openInputStream() throws IOException
        {
            in = content.getInputStream();
            return in;
        }

        @Override
        public void closeInputStream() throws IOException
        {
            IOUtils.closeQuietly(in);
            in = null;
        }

        @Override
        public void transferTo(File dest) throws IOException
        {
            FileStream.super.transferTo(dest);
        }
    }
}