package org.labkey.api.study.assay.plate;

import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.util.NumberUtilsLabKey;

/**
 * Created by klum on 12/14/14.
 */
public abstract class AbstractPlateReader implements PlateReader
{
    @Override
    public boolean isWellValueValid(double value)
    {
        // negative well values are error codes
        return value >= 0;
    }

    @Override
    public String getWellDisplayValue(Object value)
    {
        String strValue = String.valueOf(value);
        if (NumberUtilsLabKey.isNumber(strValue))
        {
            double dblValue = NumberUtils.toDouble(strValue);

            if (dblValue == WELL_NOT_COUNTED)
                return "TNTC";
        }
        return strValue;
    }
}
