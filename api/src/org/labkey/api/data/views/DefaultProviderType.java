package org.labkey.api.data.views;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Apr 2, 2012
 */
public class DefaultProviderType implements DataViewProvider.Type
{
    private String _name;
    private String _description;
    private boolean _showByDefault;

    public DefaultProviderType(String name, String description, boolean showByDefault)
    {
        assert name != null : "name cannot be null";
        assert description != null : "description cannot be null";

        _name = name;
        _description = description;
        _showByDefault = showByDefault;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    @Override
    public boolean isShowByDefault()
    {
        return _showByDefault;
    }

    @Override
    public int hashCode()
    {
        return _name.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof DefaultProviderType))
            return false;

        DefaultProviderType type = (DefaultProviderType)obj;
        if (!type.getName().equals(this.getName())) return false;

        return true;
    }
}
