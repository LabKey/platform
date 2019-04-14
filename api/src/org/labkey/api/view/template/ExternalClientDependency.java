package org.labkey.api.view.template;

import org.apache.log4j.Logger;

public class ExternalClientDependency extends ClientDependency
{
    private static final Logger _log = Logger.getLogger(ExternalClientDependency.class);

    private final String _uri;

    protected ExternalClientDependency(String uri)
    {
        super(uri, getType(uri));
        _uri = uri;
    }

    private static TYPE getType(String uri)
    {
        TYPE type = TYPE.fromString(uri);

        if (type == null)
        {
            _log.warn("External client dependency type not recognized: " + uri);
        }

        return type;
    }

    @Override
    protected String getUniqueKey()
    {
        return getCacheKey(_uri, _mode);
    }

    @Override
    public String getScriptString()
    {
        return _uri;
    }
}
