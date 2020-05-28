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

import org.jetbrains.annotations.NotNull;
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
    @NotNull
    private final Path _path;
    @Nullable
    final Resolver _resolver;

    protected AbstractResource(@NotNull Path path, @Nullable Resolver resolver)
    {
        _path = path;
        _resolver = resolver;
    }

    protected AbstractResource(@NotNull Path folder, @NotNull String name, @Nullable Resolver resolver)
    {
        this(folder.append(validateName(name)), resolver);
    }

    protected AbstractResource(@NotNull Resource folder, @NotNull String name, @Nullable Resolver resolver)
    {
        this(folder.getPath().append(validateName(name)), resolver);
    }

    private static String validateName(String name)
    {
        if (name == null || name.isEmpty())
        {
            throw new IllegalArgumentException("Name cannot be null or the empty string. Use the constructor that does not take a name instead.");
        }
        return name;
    }


    @Nullable @Override
    public Resolver getResolver()
    {
        return _resolver;
    }

    @NotNull @Override
    public Path getPath()
    {
        return _path;
    }

    @Override
    public String getName()
    {
        return _path.getName();
    }

    @Override
    public boolean exists()
    {
        return false;
    }

    @Override
    public boolean isCollection()
    {
        return false;
    }

    public boolean isCollectionType()
    {
        return false;
    }

    @Override
    public Resource find(String name)
    {
        return null;
    }

    @Override
    public boolean isFile()
    {
        return false;
    }

    @Override
    public Collection<String> listNames()
    {
        return Collections.emptyList();
    }

    @Override
    public Collection<? extends Resource> list()
    {
        return Collections.emptyList();
    }

    @Override
    public long getVersionStamp()
    {
        return getLastModified();
    }

    @Override
    public long getLastModified()
    {
        return Long.MIN_VALUE;
    }

    @Override
    public InputStream getInputStream() throws IOException
    {
        return null;
    }

    @Override
    public String toString()
    {
        Resolver r = getResolver();
        return (r == null ? "[]" : "[" + r.toString() + "] ") + getPath().toString();
    }
}
