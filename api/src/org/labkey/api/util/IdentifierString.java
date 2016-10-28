/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
package org.labkey.api.util;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.apache.commons.beanutils.ConversionException;

import java.util.regex.Pattern;

/**
 * User: matthewb
 * Date: Feb 9, 2009
 * Time: 10:40:19 AM
 */
public class IdentifierString
{
    static Pattern idPattern = Pattern.compile("[\\p{Alpha}_][\\p{Alnum}_]*");

    public static String validateIdentifierString(String s)
    {
        if (StringUtils.isEmpty(s) || idPattern.matcher(s).matches())
            return null;
        return "Value is not a valid identifier: " + s;
    }
}