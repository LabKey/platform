package org.labkey.api.data;

public interface MutableColumnConceptProperties
{
    void setPrincipalConceptCode(String principalConceptCode);
    void setConceptImportColumn(String conceptImportColumn);
    void setConceptLabelColumn(String conceptLabelColumn);
    void setSourceOntology(String sourceOntology);
    void setConceptSubtree(String conceptSubtree);
}
