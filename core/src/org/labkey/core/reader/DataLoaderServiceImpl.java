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
package org.labkey.core.reader;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderFactory;
import org.labkey.api.reader.DataLoaderService;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.webdav.WebdavResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 * Date: 9/30/12
 */
public class DataLoaderServiceImpl implements DataLoaderService
{
    private static final Logger _log = Logger.getLogger(DataLoaderService.class);

    private Map<FileType, DataLoaderFactory> _fileTypeToFactory;
    private MultiValuedMap<String, DataLoaderFactory> _extensionToFactory;
    private List<DataLoaderFactory> _factories;

    public static DataLoaderServiceImpl get()
    {
        return (DataLoaderServiceImpl) ServiceRegistry.get(DataLoaderService.class);
    }

    public DataLoaderServiceImpl()
    {
        _fileTypeToFactory = new LinkedHashMap<>();
        _extensionToFactory = new ArrayListValuedHashMap<>();
        _factories = new ArrayList<>(10);
    }

    public List<DataLoaderFactory> getFactories()
    {
        return Collections.unmodifiableList(_factories);
    }

    @Override
    public void registerFactory(@NotNull DataLoaderFactory factory)
    {
        FileType fileType = factory.getFileType();
        if (fileType == null)
            throw new IllegalArgumentException("FileType must not be null");

        for (String s : fileType.getSuffixes())
        {
            _extensionToFactory.put(s.toLowerCase(), factory);
        }

        _fileTypeToFactory.put(fileType, factory);
        _factories.add(factory);
    }

    private static byte[] getHeader(File f, InputStream in)
    {
        // Can't read header if underlying stream can't be buffered
        if (in != null && !in.markSupported())
            return null;

        InputStream is = in;

        try
        {
            if (null == is)
                is = new BufferedInputStream(new FileInputStream(f));

            is.skip(Long.MIN_VALUE);
            return FileUtil.readHeader(is, 8*1024);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to read file header");
        }
        finally
        {
            // Don't close original InputStream
            if (is != in)
                IOUtils.closeQuietly(is);
        }
    }

    private Collection<DataLoaderFactory> matches(String filename, String contentType, byte[] header, Collection<DataLoaderFactory> factories)
    {
        ArrayList<DataLoaderFactory> matches = new ArrayList<>(10);
        for (DataLoaderFactory f : factories)
        {
            FileType fileType = f.getFileType();
            if (fileType.isType(filename, contentType, header))
                matches.add(f);
        }
        return matches;
    }

    @Override
    public DataLoaderFactory findFactory(File file, FileType guessFormat)
    {
        return findFactory(file, null, guessFormat);
    }

    @Override
    public DataLoaderFactory findFactory(File file, String contentType, FileType guessFormat)
    {
        InputStream is = null;
        try
        {
            is = new BufferedInputStream(new FileInputStream(file));
            return findFactory(file.getName(), contentType, is, guessFormat);
        }
        catch (FileNotFoundException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            IOUtils.closeQuietly(is);
        }
    }

    @Override
    public DataLoaderFactory findFactory(String filename, String contentType, InputStream is, FileType guessFormat)
    {
        List<DataLoaderFactory> matches = new ArrayList<>(10);
        byte[] header = null;

        // Attempt guessFormat first
        if (guessFormat != null)
        {
            DataLoaderFactory factory = _fileTypeToFactory.get(guessFormat);
            assert factory != null : "No DataLoaderFactory registered for FileType: " + guessFormat;
            if (factory != null)
            {
                header = getHeader(null, is);
                if (guessFormat.isType(filename, contentType, header))
                    return factory;
            }
        }

        // Attempt to match on file extension alone
        String ext = FileUtil.getExtension(filename);
        if (ext != null)
        {
            ext = ext.toLowerCase();
            Collection<DataLoaderFactory> factories = _extensionToFactory.get(ext);
            if (factories.isEmpty() && !ext.startsWith("."))
                factories = _extensionToFactory.get("." + ext);
            if (factories != null && factories.size() > 0)
            {
                if (factories.size() == 1)
                {
                    matches.add(factories.iterator().next());
                }
                else
                {
                    if (header == null)
                        header = getHeader(null, is);
                    matches.addAll(matches(filename, contentType, header, factories));
                }
            }
        }

        if (matches.size() > 0)
        {
            // If more than one DataLoader matches the extension, override
            // FileType.isHeaderMatch() on your DataLoader FileType and sniff the header.
            if (matches.size() > 1)
                _log.warn("More than one DataLoader FileType matches");

            return matches.get(0);
        }

        // No file extension or all FileType for that extension didn't match.
        // Fallback to checking all factories for a match based on the file header.
        if (header == null)
            header = getHeader(null, is);

        matches.addAll(matches(null, contentType, header, _factories));

        if (matches.size() > 0)
        {
            // If more than one DataLoader matches the extension, override
            // FileType.isHeaderMatch() on your DataLoader FileType and sniff the header.
            if (matches.size() > 1)
                _log.warn("More than one DataLoader FileType matches");

            return matches.get(0);
        }

        return null;
    }

    public DataLoader createLoader(String filename, String contentType, InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer, @Nullable FileType guessFormat) throws IOException
    {
        DataLoaderFactory factory = findFactory(filename, contentType, is, guessFormat);

        // If no factory matched, attempt to use the guessFormat as the default
        if (factory == null && guessFormat != null)
            factory = _fileTypeToFactory.get(guessFormat);

        if (factory == null)
            throw new IOException("Unable to determine file format.");

        return factory.createLoader(is, hasColumnHeaders, mvIndicatorContainer);
    }

    public DataLoader createLoader(MultipartFile file, boolean hasColumnHeaders, Container mvIndicatorContainer, @Nullable FileType guessFormat) throws IOException
    {
        String origName = StringUtils.trimToEmpty(file.getOriginalFilename());
        String filename = origName.toLowerCase();

        return createLoader(filename, file.getContentType(), file.getInputStream(), hasColumnHeaders, mvIndicatorContainer, guessFormat);
    }

    public DataLoader createLoader(Resource r, boolean hasColumnHeaders, Container mvIndicatorContainer, @Nullable FileType guessFormat) throws IOException
    {
        String contentType = null;
        if (r.isFile())
        {
            File file = null;
            if (r instanceof FileResource)
                file = ((FileResource)r).getFile();
            else if (r instanceof WebdavResource)
            {
                file = ((WebdavResource)r).getFile();
                contentType = ((WebdavResource)r).getContentType();
            }

            if (null != file)
                return createLoader(file, contentType, hasColumnHeaders, mvIndicatorContainer, guessFormat);
        }

        String origName = StringUtils.trimToEmpty(r.getName());
        String filename = origName.toLowerCase();

        return createLoader(filename, null, r.getInputStream(), hasColumnHeaders, mvIndicatorContainer, guessFormat);
    }

    public DataLoader createLoader(File file, boolean hasColumnHeaders) throws IOException
    {
        return createLoader(file, null, hasColumnHeaders, null, null);
    }

    public DataLoader createLoader(File file, String contentType, boolean hasColumnHeaders, Container mvIndicatorContainer, @Nullable FileType guessFormat) throws IOException
    {
        DataLoaderFactory factory = findFactory(file, contentType, guessFormat);

        // If no factory matched, attempt to use the guessFormat as the default
        if (factory == null && guessFormat != null)
            factory = _fileTypeToFactory.get(guessFormat);

        if (factory == null)
            throw new IOException("Unable to determine file format.");

        return factory.createLoader(file, hasColumnHeaders, mvIndicatorContainer);
    }
}
