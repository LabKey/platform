/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Path;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;

/**
 * User: kevink
 * Date: Mar 12, 2010 2:30:54 PM
 */
abstract public class AbstractResource implements Resource
{
    private final Path _path;
    final Resolver _resolver;

    protected AbstractResource(Path path, @Nullable Resolver resolver)
    {
        _path = path;
        _resolver = resolver;
    }

    protected AbstractResource(Path folder, String name, Resolver resolver)
    {
        this(folder.append(name), resolver);
    }

    protected AbstractResource(Resource folder, String name, Resolver resolver)
    {
        this(folder.getPath().append(name), resolver);
    }

    public Resolver getResolver()
    {
        return _resolver;
    }

    public Path getPath()
    {
        return _path;
    }

    public String getName()
    {
        return _path.getName();
    }

    public boolean exists()
    {
        return false;
    }

    public boolean isCollection()
    {
        return false;
    }

    public boolean isCollectionType()
    {
        return false;
    }

    public Resource find(String name)
    {
        return null;
    }

    public boolean isFile()
    {
        return false;
    }

    public Collection<String> listNames()
    {
        return Collections.emptyList();
    }

    public Collection<? extends Resource> list()
    {
        return Collections.emptyList();
    }

    public long getVersionStamp()
    {
        return getLastModified();
    }
    
    public long getLastModified()
    {
        return Long.MIN_VALUE;
    }

    public InputStream getInputStream() throws IOException
    {
        return null;
    }

    public String toString()
    {
        Resolver r = getResolver();
        return (r == null ? "[]" : "[" + r.toString() + "] ") + getPath().toString();
    }
}
