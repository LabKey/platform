package org.labkey.core.admin.sitevalidation;

import org.labkey.api.admin.sitevalidation.SiteValidatorDescriptor;

/**
 * User: tgaluhn
 * Date: 10/30/2016
 */
public class SiteValidatorDescriptorImpl implements SiteValidatorDescriptor
{
    final String _name;
    final String _description;

    public SiteValidatorDescriptorImpl(String name, String description)
    {
        _name = name;
        _description = description;
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
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SiteValidatorDescriptorImpl that = (SiteValidatorDescriptorImpl) o;

        if (!_name.equals(that._name)) return false;
        return _description != null ? _description.equals(that._description) : that._description == null;

    }

    @Override
    public int hashCode()
    {
        int result = _name.hashCode();
        result = 31 * result + (_description != null ? _description.hashCode() : 0);
        return result;
    }
}
