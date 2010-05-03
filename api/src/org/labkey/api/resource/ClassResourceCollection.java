/*
 * Copyright (c) 2010 LabKey Corporation
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

import java.net.URL;
import java.util.Collection;
import java.util.Collections;

/**
 * User: kevink
 * Date: Mar 12, 2010 2:59:58 PM
 */
public class ClassResourceCollection extends AbstractResourceCollection
{
    private Class clazz;

    public ClassResourceCollection(Class clazz, Resolver resolver)
    {
        this(Path.parse("/" + clazz.getPackage().getName().replaceAll("\\.", "/")), clazz, resolver);
    }

    public ClassResourceCollection(Path path, Class clazz, Resolver resolver)
    {
        super(path, resolver);
        this.clazz = clazz;
    }

    Class getResourceClass()
    {
        return clazz;
    }

    public Resource parent()
    {
        return null;
    }

    public boolean exists()
    {
        return true;
    }

    public boolean isCollection()
    {
        return true;
    }

    public Resource find(String name)
    {
        Resource r = _find(name);
        if (r == null && !name.startsWith("/"))
            r = _find("/" + name);
        return r;
    }

    private Resource _find(String name)
    {
        URL url = clazz.getResource(name);
        if (url != null)
            return new ClassResource(this, name, _resolver);
        return null;
    }

    public Collection<String> listNames()
    {
        return Collections.emptyList();
    }

    public Collection<Resource> list()
    {
        return Collections.emptyList();
    }

}
