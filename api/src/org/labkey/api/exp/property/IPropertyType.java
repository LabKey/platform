package org.labkey.api.exp.property;

import org.labkey.api.security.User;
import org.labkey.api.data.Container;
import org.labkey.api.data.ColumnInfo;

public interface IPropertyType
{
    String getTypeURI();

    String getLabel();

    String getLabel(Container container);

    void initColumnInfo(User user, Container container, ColumnInfo column);
}
