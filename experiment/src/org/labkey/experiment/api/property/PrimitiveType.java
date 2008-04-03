package org.labkey.experiment.api.property;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.IPropertyType;
import org.labkey.api.security.User;

public class PrimitiveType implements IPropertyType
{
    PropertyType _type;
    public PrimitiveType(PropertyType type)
    {
        _type = type;
    }

    public String getName()
    {
        return _type.getXmlName();
    }

    public String getTypeURI()
    {
        return _type.getTypeUri();
    }

    public String getLabel()
    {
        return getName();
    }

    public String getLabel(Container container)
    {
        return getLabel();
    }

    public void initColumnInfo(User user, Container container, ColumnInfo column)
    {
        column.setSqlTypeName(ColumnInfo.sqlTypeNameFromSqlType(_type.getSqlType(), column.getSqlDialect()));
    }
}
