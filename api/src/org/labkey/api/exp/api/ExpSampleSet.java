package org.labkey.api.exp.api;

import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.User;

import java.util.List;

public interface ExpSampleSet extends ExpObject
{
    public String getMaterialLSIDPrefix();

    public PropertyDescriptor[] getPropertiesForType();

    public ExpMaterial[] getSamples();

    public Domain getType();

    public String getDescription();

    public PropertyDescriptor getIdCol1();
    public PropertyDescriptor getIdCol2();
    public PropertyDescriptor getIdCol3();

    void setDescription(String s);

    void setMaterialLSIDPrefix(String s);

    void insert(User user);

    List<PropertyDescriptor> getIdCols();
}
