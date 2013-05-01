/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.di;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.SystemProperty;

import java.util.Map;
import java.util.Set;


/**
 * Created with IntelliJ IDEA.
 * User: matthew
 * Date: 4/22/13
 * Time: 11:44 AM
 */
public class VariableMapImpl implements VariableMap
{
    final VariableMap _outer;
    final CaseInsensitiveHashMap<PropertyDescriptor> declarations = new CaseInsensitiveHashMap<>();
    final CaseInsensitiveHashMap<Object> values = new CaseInsensitiveHashMap<>();

    // No-args constructor to support de-serialization in Java 7
    @SuppressWarnings({"UnusedDeclaration"})
    public VariableMapImpl()
    {
        _outer = null;
    }

    public VariableMapImpl(VariableMap parentScope)
    {
        _outer = parentScope;
    }

    public VariableMapImpl(VariableMap parentScope, Map<String, ObjectProperty> propertyMap)
    {
        _outer = parentScope;
        if (propertyMap != null)
        {
            for (ObjectProperty property : propertyMap.values())
            {
                put(property.getName(), property.value());
            }
        }
    }


    @Override
    public Object get(String key)
    {
        if (null == _outer || values.containsKey(key))
            return values.get(key);
        return _outer.get(key);
    }


    @Override
    public Object put(String key, Object value)
    {
        PropertyDescriptor pd = declarations.get(key);
        if (null != pd)
            value = pd.getJdbcType().convert(value);
        return values.put(key,value);
    }


    public Object put(SystemProperty prop, Object value)
    {
        PropertyDescriptor pd = prop.getPropertyDescriptor();
        declarations.put(pd.getName(), pd);
        return put(pd.getName(), value);
    }

    @Override
    public Set<String> keySet()
    {
        return values.keySet();
    }

    @Override
    public PropertyDescriptor getDescriptor(String key)
    {
        return declarations.get(key);
    }
}
