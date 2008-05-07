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

    /**
     * Some sample sets shouldn't be updated through the standard import or derived samples
     * UI, as they don't have any properties. Study specimens are an example.
     */
    public boolean canImportMoreSamples();

    public PropertyDescriptor getIdCol1();
    public PropertyDescriptor getIdCol2();
    public PropertyDescriptor getIdCol3();

    void setDescription(String s);

    void setMaterialLSIDPrefix(String s);

    void insert(User user);

    List<PropertyDescriptor> getIdCols();

    void setIdCols(List<PropertyDescriptor> pds);
}
