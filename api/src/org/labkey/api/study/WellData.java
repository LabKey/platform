package org.labkey.api.study;

import java.io.Serializable;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 4:13:37 PM
 */
public interface WellData
{
    Plate getPlate();

    double getStdDev();

    double getMax();

    double getMin();

    double getMean();

    Double getDilution();

    void setDilution(Double dilution);
}
