/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.api.writer;

import org.apache.xmlbeans.XmlObject;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.XmlBeansUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
    private final String _root;
    private final Map<String, XmlObject> _docMap = new HashMap<>();
    private final Map<String, StringWriter> _textDocMap = new HashMap<>();
    private final Map<String, ByteArrayOutputStream> _byteDocMap = new HashMap<>();
    private final Map<String, MemoryVirtualFile> _folders = new HashMap<>();

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
        _textDocMap.put(makeLegalName(path), writer);

        return new PrintWriter(writer);
    }

    @Override
    public OutputStream getOutputStream(String filename) throws IOException
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        _byteDocMap.put(makeLegalName(filename), os);

        return new BufferedOutputStream(os);
    }

    @Override
    public InputStream getInputStream(String filename) throws IOException
    {
        XmlObject doc = _docMap.get(makeLegalName(filename));
        if (doc != null)
            return doc.newInputStream(XmlBeansUtil.getDefaultSaveOptions());

        StringWriter writer = _textDocMap.get(makeLegalName(filename));
        if (writer != null)
        {
            String contents = writer.getBuffer().toString();
            return new BufferedInputStream(new ByteArrayInputStream(contents.getBytes(StringUtilsLabKey.DEFAULT_CHARSET)));
        }

        ByteArrayOutputStream os = _byteDocMap.get(makeLegalName(filename));
        if (os != null)
        {
            return new BufferedInputStream(new ByteArrayInputStream(os.toByteArray()));
        }

        return null;
    }

    @Override
    public void saveXmlBean(String filename, XmlObject doc) throws IOException
    {
        _docMap.put(makeLegalName(filename), doc);    // TODO: Shouldn't this validate before saving?  That would match the other VF impls
    }

    @Override
    public VirtualFile createZipArchive(String name) throws IOException
    {
        return this;
    }

    @Override
    public VirtualFile getDir(String path)
    {
        String newPath = makeLegalName(path);
        if (!_folders.containsKey(newPath))
        {
            _folders.put(newPath, new MemoryVirtualFile(newPath));
        }
        return _folders.get(newPath);
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
        return _docMap.get(makeLegalName(filename));
    }

    @Override
    public XmlObject getXmlBean(String filename) throws IOException
    {
        return _docMap.get(makeLegalName(filename));
    }

    @Override
    public String getRelativePath(String filename)
    {
        return _root + File.separator + makeLegalName(filename);
    }

    @Override
    public List<String> list()
    {
        List<String> names = new ArrayList<>();

        names.addAll(_docMap.keySet());
        names.addAll(_textDocMap.keySet());
        names.addAll(_byteDocMap.keySet());

        return names;
    }

    @Override
    public List<String> listDirs()
    {
        return new ArrayList<>(_folders.keySet());
    }

    @Override
    public boolean delete(String filename)
    {
        if (_docMap.containsKey(makeLegalName(filename)))
        {
            _docMap.remove(makeLegalName(filename));
            return true;
        }
        return false;
    }

    @Override
    public void close() throws IOException
    {
        // no op
    }
}
