package org.labkey.api.exp.api;

import org.jetbrains.annotations.Nullable;

public abstract class AbstractProtocolInputCriteria implements ExpProtocolInputCriteria
{
    protected final String _config;

    protected AbstractProtocolInputCriteria(@Nullable String config)
    {
        _config = config;
    }

    @Override
    public String serializeConfig()
    {
        return _config;
    }

}
