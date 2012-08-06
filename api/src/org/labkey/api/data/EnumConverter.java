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

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

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
public class EnumConverter<E extends Enum> implements Converter
{
    private Class<E> _enumClass;
    private E[] _ordinals;

    public EnumConverter(Class<E> enumClass)
    {
        _enumClass = enumClass;
        Set<E> enumValues = EnumSet.allOf(enumClass);
        _ordinals = (E[])new Enum[enumValues.size()];
        for (E anEnum : enumValues)
        {
            _ordinals[anEnum.ordinal()] = anEnum;
        }
    }

    public static void registerEnum(Class<? extends Enum> clazz)
    {
        ConvertUtils.register(new EnumConverter(clazz), clazz);
    }

    @SuppressWarnings("unchecked")
    public Object convert(Class type, Object value)
    {
        if (value == null)
            return null;
        try
        {
            return Enum.valueOf(type, value.toString());
        }
        catch (IllegalArgumentException e)
        {
            try
            {
                int ordinal = Integer.parseInt(value.toString());
                if (ordinal >= 0 && ordinal <= _ordinals.length)
                {
                    return _ordinals[ordinal];
                }
            }
            // That's OK, not an ordinal value for the enum
            catch (NumberFormatException ignored) {}
            throw e;
        }
    }
}
