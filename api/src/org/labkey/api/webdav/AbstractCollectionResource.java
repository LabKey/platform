/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.labkey.api.security.User;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.Path;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Oct 22, 2008
 * Time: 2:49:44 PM
 */
public abstract class AbstractCollectionResource extends AbstractResource
{
    protected AbstractCollectionResource(Path path)
    {
        super(path);
    }
    
    protected AbstractCollectionResource(Path parent, String name)
    {
        super(parent, name);
    }

    public boolean isCollection()
    {
        return exists();
    }

    public boolean isFile()
    {
        return false;
    }

    public String getModifiedBy()
    {
        return null;
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

    public List<Resource> list()
    {
        List<String> names = listNames();
        List<Resource> list = new ArrayList<Resource>(names.size());
        for (String name : names)
        {
            Resource r = find(name);
            if (r != null)
                list.add(r);
        }
        return list;
    }

    @NotNull
    public List<WebdavResolver.History> getHistory()
    {
        //noinspection unchecked
        return Collections.EMPTY_LIST;
    }
}
