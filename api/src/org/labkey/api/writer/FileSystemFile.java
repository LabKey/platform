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
package org.labkey.api.writer;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.admin.ImportException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.MinorConfigurationException;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 3:30:23 PM
 */
public class FileSystemFile extends AbstractVirtualFile
{
    private final Path _root;

    // Required for xstream serialization on Java 7
    @SuppressWarnings({"UnusedDeclaration"})
    private FileSystemFile()
    {
        _root = null;
    }

    public FileSystemFile(File root)
    {
        this(root.toPath());
    }

    public FileSystemFile(Path root)
    {
        try
        {
            ensureWriteableDirectory(root);
            _root = root;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getLocation()
    {
        return _root.toAbsolutePath().toString();
    }

    @Override
    public PrintWriter getPrintWriter(String filename) throws IOException
    {
        File file = new File(_root.toFile(), makeLegalName(filename));

        return PrintWriters.getPrintWriter(file);
    }

    @Override
    public OutputStream getOutputStream(String filename) throws IOException
    {
        Path filepath = _root.resolve(makeLegalName(filename));
        return Files.newOutputStream(filepath);
    }

    @Override
    public void saveXmlBean(String filename, XmlObject doc) throws IOException
    {
        try
        {
            XmlBeansUtil.validateXmlDocument(doc, filename);
        }
        catch (XmlValidationException e)
        {
            throw new RuntimeException(e);
        }

        saveXmlBean(filename, doc, XmlBeansUtil.getDefaultSaveOptions());
    }

    // Expose this if/when some caller needs to customize the options
    private void saveXmlBean(String filename, XmlObject doc, XmlOptions options) throws IOException
    {
        Path file = _root.resolve(makeLegalName(filename));
        doc.save(file.toFile(), options);
    }

    @Override
    public VirtualFile getDir(String name)
    {
        return new FileSystemFile(_root.resolve(makeLegalName(name)));
    }

    @Override
    public VirtualFile createZipArchive(String name) throws IOException
    {
        return new ZipFile(_root, name);
    }

    @Override
    public String makeLegalName(String name)
    {
        return makeLegal(name);
    }

    public static String makeLegal(String name)
    {
        return FileUtil.makeLegalName(name);
    }

    public static void ensureWriteableDirectory(Path dir) throws IOException
    {
        if (!Files.exists(dir))
            Files.createDirectories(dir);

        if (!Files.isDirectory(dir))
            throw new MinorConfigurationException(dir.toAbsolutePath() + " is not a directory.");

        if (!Files.isWritable(dir))
            throw new MinorConfigurationException("Can't write to " + dir.toAbsolutePath());
    }

    @Override
    public XmlObject getXmlBean(String filename) throws IOException
    {
        Path file = _root.resolve(makeLegalName(filename));

        if (Files.exists(file))
        {
            try (InputStream inputStream = Files.newInputStream(file))
            {
                return XmlObject.Factory.parse(inputStream, XmlBeansUtil.getDefaultParseOptions());
            }
            catch (XmlException e)
            {
                throw new IOException(e);
            }
        }
        return null;
    }

    @Override
    public InputStream getInputStream(String filename) throws IOException
    {
        Path file = _root.resolve(makeLegalName(filename));

        if (Files.exists(file))
            return new BufferedInputStream(Files.newInputStream(file));
        else
            return null;
    }

    @Override
    public String getRelativePath(String filename)
    {
        Path file = _root.resolve(makeLegalName(filename));
        return ImportException.getRelativePath(_root, file);
    }

    @Override
    public List<String> list()
    {
        return list(Files::isRegularFile);
    }

    private List<String> list(Predicate<Path> filter)
    {
        try (Stream<Path> files = Files.list(_root))
        {
            return null == files ? Collections.emptyList() :
                    files.filter(filter)
                         .map(Path::getFileName)
                         .map(Path::toString)
                         .collect(Collectors.toList());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> listDirs()
    {
        return list(Files::isDirectory);
    }

    @Override
    public boolean delete(String filename)
    {
        Path file = _root.resolve(makeLegalName(filename));
        try
        {
            return Files.deleteIfExists(file);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close()
    {
        // no op
    }
}
