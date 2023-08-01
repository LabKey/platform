package org.labkey.assay.plate.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlateField
{
    private String _label;
    private String _name;
    private String _propertyURI;

    public PlateField()
    {
    }

    public PlateField(String name, String label, String propertyURI)
    {
        _name = name;
        _label = label;
        _propertyURI = propertyURI;
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
}
