package org.labkey.api.attachments;

import org.apache.struts.upload.CommonsMultipartRequestHandler;
import org.apache.struts.upload.FormFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Sep 10, 2007
 * Time: 9:14:24 PM
 */
public class StrutsAttachmentFile implements AttachmentFile
{
    private FormFile _file;
    private InputStream _in;
    private String _filename = null;

    public StrutsAttachmentFile(FormFile file)
    {
        _file = file;
    }

    public byte[] getBytes() throws IOException
    {
        return _file.getFileData();
    }

    public String getContentType()
    {
        return _file.getContentType();
    }

    public String getFilename()
    {
        return null != _filename ? _filename : _file.getFileName();
    }

    public void setFilename(String filename)
    {
        _filename = filename;
    }

    public InputStream openInputStream() throws IOException
    {
        _in = _file.getInputStream();
        return _in;
    }

    public void closeInputStream() throws IOException
    {
        if (null != _in)
            _in.close();
    }

    public long getSize()
    {
        return _file.getFileSize();
    }

    private boolean isEmpty()
    {
        return isEmpty(_file);
    }

    public String getError()
    {
        if (!isEmpty() && 0 == getSize())
            return "Warning: " + getFilename() + " was not uploaded.  Attachments must not exceed the maximum file size of " + getMaxFileUploadSize() + ".";
        else
            return null;
    }

    private String getMaxFileUploadSize()
    {
        // For now, assume we're configured for the default maximum struts file size
        // TODO: Should query Struts to determine the configured max file size
        return (CommonsMultipartRequestHandler.DEFAULT_SIZE_MAX / (1024 * 1024)) + "MB";
    }

    public static List<AttachmentFile> createList(Map<String, FormFile> fileMap)
    {
        FormFile[] formFiles = fileMap.values().toArray(new FormFile[fileMap.size()]);
        return createList(formFiles);
    }

    public static List<AttachmentFile> createList(FormFile... formFiles)
    {
        List<AttachmentFile> files = new ArrayList<AttachmentFile>(formFiles.length);

        for (FormFile file : formFiles)
            if (!isEmpty(file))
                files.add(new StrutsAttachmentFile(file));

        return files;
    }

    private static boolean isEmpty(FormFile file)
    {
        return (null == file || null == file.getFileName() || 0 == file.getFileName().length());
    }
}
