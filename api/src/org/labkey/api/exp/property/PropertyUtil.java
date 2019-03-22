/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
package org.labkey.api.exp.property;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.labkey.api.exp.PropertyType;

import java.text.DecimalFormat;

/*
* User: Karl Lum
* Date: Sep 9, 2008
* Time: 1:40:16 PM
*/
public class PropertyUtil
{
    public static Object formatValue(PropertyType type, String formatString, Object value, String defaultFormat)
    {
        if (formatString == null)
            formatString = defaultFormat;

        if (formatString != null)
        {
            if (type == PropertyType.DATE_TIME)
                return FastDateFormat.getInstance(formatString).format(value);

            else if (type == PropertyType.DOUBLE || type == PropertyType.INTEGER)
            {
                if (!(value instanceof Number))
                    value = NumberUtils.createNumber(String.valueOf(value));
                return new DecimalFormat(formatString).format(value);
            }
        }
        return value;
    }
}