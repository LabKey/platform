/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.api.webdav;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.User;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.Path;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Convenience base class for WebDav entities that contain other collections or file-style resources.
 * User: matthewb
 * Date: Oct 22, 2008
 */
public abstract class AbstractWebdavResourceCollection extends AbstractWebdavResource
{
    protected AbstractWebdavResourceCollection(Path path)
    {
        super(path);
    }

    protected AbstractWebdavResourceCollection(Path path, WebdavResolver resolver)
    {
        super(path, resolver);
    }
    
    protected AbstractWebdavResourceCollection(Path parent, String name)
    {
        super(parent, name);
    }

    protected AbstractWebdavResourceCollection(Path parent, String name, WebdavResolver resolver)
    {
        super(parent, name, resolver);
    }

    @Override
    public boolean isCollectionType()
    {
        return true;
    }

    public boolean isCollection()
    {
        return exists();
    }

    public boolean isFile()
    {
        return false;
    }

    public InputStream getInputStream(User user) throws IOException
    {
        throw new IllegalStateException("Can't read from a collection");
    }

    public long copyFrom(User user, FileStream in) throws IOException
    {
        throw new IllegalStateException("Can't write to a collection");
    }

    public long getContentLength()
    {
        return 0;
    }

    abstract public WebdavResource find(String name);

    public Collection<? extends WebdavResource> list()
    {
        Collection<String> names = listNames();
        List<WebdavResource> list = new ArrayList<>(names.size());
        for (String name : names)
        {
            WebdavResource r = find(name);
            if (r != null)
                list.add(r);
        }
        return list;
    }

    @NotNull
    public Collection<WebdavResolver.History> getHistory()
    {
        return Collections.emptyList();
    }
}
