package org.labkey.api.resource;

import org.labkey.api.util.Path;

import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User: kevink
 * Date: Mar 12, 2010 2:59:58 PM
 */
public class ClassResourceCollection extends AbstractResourceCollection
{
    private Class clazz;

    public ClassResourceCollection(Class clazz)
    {
        this(Path.parse("/" + clazz.getPackage().getName().replaceAll("\\.", "/")), clazz);
    }

    public ClassResourceCollection(Path path, Class clazz)
    {
        super(path);
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
        URL url = clazz.getResource(name);
        if (url != null)
            return new ClassResource(this, name);
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
