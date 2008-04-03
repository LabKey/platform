package org.labkey.api.exp.property;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;

public class Lookup
{
    Container _container;
    String _schemaName;
    String _queryName;

    public Lookup()
    {
    }

    public Lookup(Container c, String schema, String query)
    {
        _container = c;
        _schemaName = schema;
        _queryName = query;
    }

    public Container getContainer()
    {
        return _container;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public void setSchemaName(String name)
    {
        _schemaName = name;
    }

    public void setQueryName(String name)
    {
        _queryName = name;
    }
}
