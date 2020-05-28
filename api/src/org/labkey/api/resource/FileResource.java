/*
 * Copyright (c) 2010-2019 LabKey Corporation
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
package org.labkey.api.resource;

import org.labkey.api.util.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * User: kevink
 * Date: Mar 12, 2010 3:03:42 PM
 */
public class FileResource extends AbstractResource
{
    private final File _file;

    FileResource(Path path, File file, Resolver resolver)
    {
        super(path, resolver);
        _file = file;
    }

    @Override
    public boolean exists()
    {
        return _file.isFile();
    }

    @Override
    public Resource parent()
    {
        return _resolver.lookup(getPath().getParent());
    }

    @Override
    public boolean isFile()
    {
        return exists();
    }

    @Override
    public long getLastModified()
    {
        return exists() ? _file.lastModified() : Long.MIN_VALUE;
    }

    @Override
    public InputStream getInputStream() throws IOException
    {
        assert isFile() : _file.toString() + " is not a file";
        if (isFile())
            return new FileInputStream(_file);
        return null;
    }

    // TODO move more functionality into interface and remove this method
    public File getFile()
    {
        return _file;
    }
}
