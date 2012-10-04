/*
 * Copyright (c) 2012 LabKey Corporation
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

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderFactory;
import org.labkey.api.reader.DataLoaderService;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
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
import java.util.List;

/**
 * User: kevink
 * Date: 9/30/12
 */
public class DataLoaderServiceImpl implements DataLoaderService.I
{
    private static final Logger _log = Logger.getLogger(DataLoaderService.class);

    private MultiMap<String, DataLoaderFactory> _extensionToFactory;
    private List<DataLoaderFactory> _factories;

    public DataLoaderServiceImpl()
    {
        _extensionToFactory = new MultiHashMap<String, DataLoaderFactory>();
        _factories = new ArrayList<DataLoaderFactory>(10);
    }

    @Override
    public void registerFactory(@NotNull DataLoaderFactory factory)
    {
        FileType fileType = factory.getFileType();
        if (fileType == null)
            throw new IllegalArgumentException("FileType must not be null");

        for (String s : fileType.getSuffixes())
        {
            _extensionToFactory.put(s, factory);
        }

        _factories.add(factory);
    }

    private byte[] getHeader(File f, InputStream in)
    {
        InputStream is = in;

        boolean buffered = false;
        try
        {
            if (null == is)
                is = new FileInputStream(f);

            if (!is.markSupported())
            {
                buffered = true;
                is = new BufferedInputStream(is);
            }

            is.skip(Long.MIN_VALUE);
            return FileUtil.readHeader(is, 4*1024);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to read file header");
        }
        finally
        {
            // Don't close original InputStream
            if (is != in && !buffered)
                IOUtils.closeQuietly(is);
        }
    }

    private Collection<DataLoaderFactory> matches(String filename, String contentType, byte[] header, Collection<DataLoaderFactory> factories)
    {
        ArrayList<DataLoaderFactory> matches = new ArrayList<DataLoaderFactory>(10);
        for (DataLoaderFactory f : factories)
        {
            FileType fileType = f.getFileType();
            if (fileType.isType(filename, contentType, header))
                matches.add(f);
        }
        return matches;
    }

    @Override
    public DataLoaderFactory findFactory(File file)
    {
        return findFactory(file, null);
    }

    @Override
    public DataLoaderFactory findFactory(File file, String contentType)
    {
        FileInputStream fis = null;
        try
        {
            fis = new FileInputStream(file);
            return findFactory(file.getName(), contentType, fis);
        }
        catch (FileNotFoundException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            IOUtils.closeQuietly(fis);
        }
    }

    @Override
    public DataLoaderFactory findFactory(String filename, String contentType, InputStream is)
    {
        List<DataLoaderFactory> matches = new ArrayList<DataLoaderFactory>(10);
        byte[] header = null;

        // Attempt to match on file extension alone
        String ext = FileUtil.getExtension(filename);
        if (ext != null)
        {
            Collection<DataLoaderFactory> factories = _extensionToFactory.get(ext);
            if (factories != null && factories.size() > 0)
            {
                if (factories.size() == 1)
                {
                    matches.add(factories.iterator().next());
                }
                else
                {
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

    public DataLoader createLoader(String filename, String contentType, InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        DataLoaderFactory factory = findFactory(filename, contentType, is);
        if (factory == null)
            throw new IOException("Unknown file type.");

        return factory.createLoader(is, hasColumnHeaders, mvIndicatorContainer);
    }

    public DataLoader createLoader(MultipartFile file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        String origName = StringUtils.trimToEmpty(file.getOriginalFilename());
        String filename = origName.toLowerCase();

        return createLoader(filename, file.getContentType(), file.getInputStream(), hasColumnHeaders, mvIndicatorContainer);
    }

    public DataLoader createLoader(Resource r, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
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
                return createLoader(file, contentType, hasColumnHeaders, mvIndicatorContainer);
        }

        String origName = StringUtils.trimToEmpty(r.getName());
        String filename = origName.toLowerCase();

        return createLoader(filename, null, r.getInputStream(), hasColumnHeaders, mvIndicatorContainer);
    }

    public DataLoader createLoader(File file, boolean hasColumnHeaders) throws IOException
    {
        return createLoader(file, null, hasColumnHeaders, null);
    }

    public DataLoader createLoader(File file, String contentType, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        DataLoaderFactory factory = findFactory(file, contentType);
        if (factory == null)
            throw new IOException("Unknown file type.");

        return factory.createLoader(file, hasColumnHeaders, mvIndicatorContainer);
    }
}
