/*
 * Copyright (c) 2014-2018 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;

/**
 * Validate that a string value is not longer than the column's scale.
 */
public class LengthValidator extends AbstractColumnValidator
{
    private final int scale;

    public LengthValidator(String columnName, int scale)
    {
        super(columnName);
        this.scale = scale;
    }

    @Override
    public String _validate(int rowNum, Object value)
    {
        if (value instanceof String)
        {
            String s = (String)value;
            if (s.length() > scale)
                return "Value is too long for column '" + _columnName + "', a maximum length of " + scale + " is allowed. The supplied value, '" + StringUtils.abbreviateMiddle(s, "...", 50) + "', was " + s.length() + " characters long.";
        }

        return null;
    }
}
