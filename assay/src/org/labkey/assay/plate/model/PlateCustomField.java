package org.labkey.assay.plate.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.labkey.api.exp.property.DomainProperty;

/**
 * Represents a custom field that is configured for a specific plate
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlateCustomField
{
    private String _label;
    private String _name;
    private String _propertyURI;
    private String _rangeURI;
    private String _container;

    public PlateCustomField()
    {
    }

    public PlateCustomField(DomainProperty prop)
    {
        _name = prop.getName();
        _label = prop.getLabel();
        _propertyURI = prop.getPropertyURI();
        _rangeURI = prop.getRangeURI();
        _container = prop.getContainer().getId();
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getPropertyURI()
    {
        return _propertyURI;
    }

    public void setPropertyURI(String propertyURI)
    {
        _propertyURI = propertyURI;
    }

    public String getRangeURI()
    {
        return _rangeURI;
    }

    public void setRangeURI(String rangeURI)
    {
        _rangeURI = rangeURI;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }
}
