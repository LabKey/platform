package org.labkey.api.resource;

import org.labkey.api.security.User;
import org.labkey.api.util.FileStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User: kevink
 * Date: Mar 12, 2010 3:00:39 PM
 */
public class ClassResource extends AbstractResource
{
    private ClassResourceCollection parent;

    public ClassResource(ClassResourceCollection parent, String name)
    {
        super(parent, name);
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
