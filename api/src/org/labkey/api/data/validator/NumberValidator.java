/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.data.validator;

/**
 * Validate float is not NaN.
 */
public class NumberValidator extends AbstractColumnValidator
{
    public NumberValidator(String columnName)
    {
        super(columnName);
    }

    @Override
    public String _validate(int rowNum, Object value)
    {
        if (!(value instanceof Number))
            return null;
        Double d = ((Number)value).doubleValue();
        if (d.isInfinite())
            return "Infinity is not a valid value for column '" + _columnName + "'";
        if (d.isNaN())
            return "NaN is not a valid value for column '" + _columnName + "'";
        return null;
    }
}
