/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.data;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;

/**
 * Converts a string to an enum. Each enum type
 * needs to be explicitly registered
 * unfortunately, since beanutils will not walk the class
 * hierarchy for a given type.
 *
 * User: jgarms
 * Date: Jul 15, 2008
 * Time: 1:17:23 PM
 */
public class EnumConverter implements Converter
{
    private static final EnumConverter CONVERTER = new EnumConverter();

    public static void registerEnum(Class<? extends Enum> clazz)
    {
        ConvertUtils.register(CONVERTER, clazz);
    }

    @SuppressWarnings("unchecked")
    public Object convert(Class type, Object value)
    {
        if (value == null)
            return null;
        return Enum.valueOf(type, value.toString());
    }
}
