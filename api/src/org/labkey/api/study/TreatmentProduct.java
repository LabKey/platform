package org.labkey.api.study;

/**
 * User: cnathe
 * Date: 12/27/13
 */
public interface TreatmentProduct
{
    int getRowId();
    int getTreatmentId();
    int getProductId();
    String getDose();
    String getRoute();
}
