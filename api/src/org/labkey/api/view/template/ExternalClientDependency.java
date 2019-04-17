package org.labkey.api.view.template;

import org.apache.log4j.Logger;
import org.labkey.clientLibrary.xml.ModeTypeEnum;

/**
 * Handles references to resources that reside on a third-party server, such as a CDN
 */
public class ExternalClientDependency extends ClientDependency
{
    private static final Logger _log = Logger.getLogger(ExternalClientDependency.class);

    private final String _uri;

    protected ExternalClientDependency(String uri, ModeTypeEnum.Enum mode)
    {
        super(getType(uri), mode);
        _devModePath = _prodModePath = _uri = uri;
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
    protected void init()
    {
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
