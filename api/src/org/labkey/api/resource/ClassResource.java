/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

import org.labkey.api.security.User;
import org.labkey.api.util.FileStream;

import java.io.IOException;
import java.io.InputStream;

/**
 * User: kevink
 * Date: Mar 12, 2010 3:00:39 PM
 */
public class ClassResource extends AbstractResource
{
    private ClassResourceCollection parent;

    public ClassResource(ClassResourceCollection parent, String name, Resolver resolver)
    {
        super(parent, name, resolver);
        this.parent = parent;
    }

    public Resource parent()
    {
        return parent;
    }

    public boolean exists()
    {
        return parent.getResourceClass().getResource(getName()) != null;
    }

    @Override
    public boolean isFile()
    {
        return true;
    }

    public InputStream getInputStream() throws IOException
    {
        return parent.getResourceClass().getResourceAsStream(getName());
    }

    public long copyFrom(User user, FileStream in) throws IOException
    {
        throw new UnsupportedOperationException();
    }

    public long getContentLength() throws IOException
    {
        throw new UnsupportedOperationException();
    }
}
