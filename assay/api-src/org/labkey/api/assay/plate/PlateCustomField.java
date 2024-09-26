package org.labkey.api.assay.plate;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;

import java.util.Objects;

/**
 * Represents a custom field that is configured for a specific plate
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlateCustomField
{
    private String _label;
    private String _name;
    private String _propertyURI;
    private String _fieldKey;
    private String _rangeURI;
    private String _container;

    public PlateCustomField()
    {
    }

    public PlateCustomField(FieldKey fieldKey)
    {
        _fieldKey = fieldKey.getName();
        _name = fieldKey.getName();
    }

    public PlateCustomField(ColumnInfo columnInfo)
    {
        _fieldKey = columnInfo.getFieldKey().getName();
        _name = columnInfo.getName();
    }

    public PlateCustomField(String propertyURI)
    {
        _propertyURI = propertyURI;
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

    public String getFieldKey()
    {
        return _fieldKey;
    }

    public void setFieldKey(String fieldKey)
    {
        _fieldKey = fieldKey;
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

    @Override
    public int hashCode()
    {
        return Objects.hash(_name, _label, _propertyURI, _rangeURI, _container);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final PlateCustomField field = (PlateCustomField) o;

        return (
            Objects.equals(this.getName(), field.getName()) &&
            Objects.equals(this.getFieldKey(), field.getFieldKey()) &&
            Objects.equals(this.getPropertyURI(), field.getPropertyURI()) &&
            Objects.equals(this.getRangeURI(), field.getRangeURI()) &&
            Objects.equals(this.getLabel(), field.getLabel()) &&
            Objects.equals(this.getContainer(), field.getContainer())
        );
    }
}
