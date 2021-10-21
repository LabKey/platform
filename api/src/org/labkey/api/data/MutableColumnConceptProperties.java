package org.labkey.api.data;

public interface MutableColumnConceptProperties
{
    public void setPrincipalConceptCode(String principalConceptCode);
    public void setConceptImportColumn(String conceptImportColumn);
    public void setConceptLabelColumn(String conceptLabelColumn);
    public void setSourceOntology(String sourceOntology);
    public void setConceptSubtree(String conceptSubtree);
}
