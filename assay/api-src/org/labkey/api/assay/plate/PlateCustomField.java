package org.labkey.api.assay.plate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
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
    private FieldKey _fieldKey;
    private String _rangeURI;
    private String _container;

    public PlateCustomField()
    {
    }

    public PlateCustomField(@NotNull PlateCustomField field)
    {
        _label = field._label;
        _name = field._name;
        _propertyURI = field._propertyURI;
        _fieldKey = field._fieldKey;
        _rangeURI = field._rangeURI;
        _container = field._container;
    }

    public PlateCustomField(@NotNull FieldKey fieldKey)
    {
        _fieldKey = fieldKey;
        _name = fieldKey.getName();
    }

    public PlateCustomField(@NotNull String propertyURI)
    {
        _propertyURI = propertyURI;
    }

    public PlateCustomField(@NotNull ColumnInfo columnInfo)
    {
        _fieldKey = columnInfo.getFieldKey();
        _name = columnInfo.getName();
        _label = columnInfo.getLabel();
        _rangeURI = columnInfo.getRangeURI();
    }

    public PlateCustomField(DomainProperty prop)
    {
        _name = prop.getName();
        _label = prop.getLabel();
        _propertyURI = prop.getPropertyURI();
        _rangeURI = prop.getRangeURI();
        _container = prop.getContainer().getId();
    }

    @JsonIgnore
    public boolean isBuiltIn()
    {
        return _fieldKey != null;
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

    @JsonIgnore
    public FieldKey getFieldKey()
    {
        return _fieldKey;
    }

    public void setFieldKey(FieldKey fieldKey)
    {
        _fieldKey = fieldKey;
    }

    public void setFieldKey(String fieldKey)
    {
        setFieldKey(FieldKey.fromParts(fieldKey));
    }

    @JsonProperty("fieldKey")
    public String getFieldKeyString()
    {
        return _fieldKey == null ? null : _fieldKey.toString();
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
        return Objects.hash(_name, _fieldKey, _label, _propertyURI, _rangeURI, _container);
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
