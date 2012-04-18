package org.labkey.api.writer;

import org.apache.xmlbeans.XmlObject;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.XmlBeansUtil;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemoryVirtualFile extends AbstractVirtualFile
{
    private String _root;
    private Map<String, XmlObject> _docMap = new HashMap<String, XmlObject>();
    private Map<String, StringWriter> _textDocMap = new HashMap<String, StringWriter>();
    private Map<String, MemoryVirtualFile> _folders = new HashMap<String, MemoryVirtualFile>();

    public MemoryVirtualFile()
    {
        this("");
    }

    public MemoryVirtualFile(String root)
    {
        _root = root;
    }

    @Override
    public PrintWriter getPrintWriter(String path) throws IOException
    {
        StringWriter writer = new StringWriter();
        _textDocMap.put(path, writer);

        return new PrintWriter(writer);
    }

    @Override
    public OutputStream getOutputStream(String filename) throws IOException
    {
        throw new UnsupportedOperationException("getOutputStream not supported by memory virtual files");
    }

    @Override
    public InputStream getInputStream(String filename) throws IOException
    {
        XmlObject doc = _docMap.get(filename);
        if (doc != null)
            return doc.newInputStream(XmlBeansUtil.getDefaultSaveOptions());

        StringWriter writer = _textDocMap.get(filename);
        if (writer != null)
        {
            String contents = writer.getBuffer().toString();
            return new BufferedInputStream(new ByteArrayInputStream(contents.getBytes()));
        }

        // TODO: add new map for output stream here

        return null;
    }

    @Override
    public void saveXmlBean(String filename, XmlObject doc) throws IOException
    {
        _docMap.put(filename, doc);
    }

    @Override
    public Archive createZipArchive(String name) throws IOException
    {
        throw new UnsupportedOperationException("Creating zip archives is not supported by memory virtual files");
    }

    @Override
    public VirtualFile getDir(String path)
    {
        if (!_folders.containsKey(path))
        {
            _folders.put(path, new MemoryVirtualFile(path));
        }
        return _folders.get(path);
    }

    @Override
    public String makeLegalName(String name)
    {
        return FileUtil.makeLegalName(name);
    }

    @Override
    public String getLocation()
    {
        return "memoryVirtualFile";
    }

    public XmlObject getDoc(String filename)
    {
        return _docMap.get(filename);
    }

    @Override
    public XmlObject getXmlBean(String filename) throws IOException
    {
        return _docMap.get(filename);
    }

    @Override
    public String getRelativePath(String filename)
    {
        return _root + File.separator + filename;
    }

    @Override
    public String[] list()
    {
        List<String> names = new ArrayList<String>();

        names.addAll(_docMap.keySet());
        names.addAll(_textDocMap.keySet());

        return names.toArray(new String[names.size()]);
    }
}
