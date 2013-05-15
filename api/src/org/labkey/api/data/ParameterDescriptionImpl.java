package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: kevink
 * Date: 5/13/13
 */
public class ParameterDescriptionImpl implements ParameterDescription
{
    protected final String _name;
    protected final String _uri;
    protected final JdbcType _type;

    public ParameterDescriptionImpl(@NotNull String name, @NotNull JdbcType type, @Nullable String uri)
    {
        _name = name;
        _type = type;
        if (uri == null)
            uri = "#" + name;
        _uri = uri;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public String getURI()
    {
        return _uri;
    }

    @Override
    public JdbcType getJdbcType()
    {
        return _type;
    }
}
