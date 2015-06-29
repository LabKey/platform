/*
 * Copyright (c) 2014-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.study.assay.plate;

import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.util.NumberUtilsLabKey;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
            else if (dblValue == WELL_OFF_SCALE)
                return "0";
        }
        return strValue;
    }

    @Override
    public Map<String, double[][]> loadMultiGridFile(PlateTemplate template, File dataFile) throws ExperimentException
    {
        throw new UnsupportedOperationException("loading multiple grids for this reader implementation is not supported");
    }

    /**
     * Converts the string token value to a numeric well value.
     * @param token
     * @return
     * @throws ValidationException - if the value cannot be converted, will cause the entire upload to fail
     */
    public double convertWellValue(String token) throws ValidationException
    {
        if (!NumberUtilsLabKey.isNumber(token))
        {
            throw new ValidationException("The specified well value: " + token + " could not be converted into a numeric value");
        }
        else
        {
            return NumberUtils.toDouble(token);
        }
    }
}
