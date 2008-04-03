package org.labkey.api.exp.property;

import org.labkey.api.security.User;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.view.ActionURL;
import org.labkey.api.exp.PropertyDescriptor;

public interface DomainProperty
{
    int getPropertyId();
    Container getContainer();
    String getPropertyURI();
    String getName();
    String getDescription();
    String getFormatString();
    String getLabel();
    ActionURL detailsURL();
    
    Domain getDomain();
    IPropertyType getType();
    boolean isRequired();

    void delete();

    void setName(String name);
    void setDescription(String description);
    void setLabel(String caption);
    void setType(IPropertyType type);
    void setPropertyURI(String uri);
    void setRangeURI(String uri);
    void setFormat(String s);
    void setRequired(boolean b);

    void initColumn(User user, ColumnInfo column);

    SQLFragment getValueSQL();
    int getSqlType();
    int getScale();
    String getInputType();

    Lookup getLookup();

    void setLookup(Lookup lookup);

    @Deprecated
    PropertyDescriptor getPropertyDescriptor();
}
