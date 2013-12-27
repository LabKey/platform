package org.labkey.api.study;

/**
 * User: cnathe
 * Date: 12/26/13
 */
public interface ProductAntigen
{
    int getRowId();
    int getProductId();
    String getGene();
    String getSubType();
    String getGenBankId();
    String getSequence();
}
