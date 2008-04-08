package org.labkey.api.data;

import java.io.Serializable;

/**
 * User: adam
 * Date: Apr 2, 2008
 * Time: 8:45:15 PM
 */
public class Project implements Serializable
{
    private Container _c;

    public Project(Container c)
    {
        if (!c.isProject())
            throw new IllegalStateException(c.getPath() + " is not a project");

        _c = c;
    }

    public Container getContainer()
    {
        return _c;
    }

    public String getName()
    {
        return getContainer().getName();
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Project project = (Project) o;

        if (_c != null ? !_c.equals(project._c) : project._c != null) return false;

        return true;
    }

    public int hashCode()
    {
        return (_c != null ? _c.hashCode() : 0);
    }
}
