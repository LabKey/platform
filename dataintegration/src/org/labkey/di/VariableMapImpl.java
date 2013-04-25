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


/**
 * Created with IntelliJ IDEA.
 * User: matthew
 * Date: 4/22/13
 * Time: 11:44 AM
 */
public class VariableMapImpl implements VariableMap
{
    final VariableMap _outer;
    final CaseInsensitiveHashMap<VariableDescription> declarations = new CaseInsensitiveHashMap<>();
    final CaseInsensitiveHashMap<Object> values = new CaseInsensitiveHashMap<>();

    public VariableMapImpl(VariableMap parentScope)
    {
        _outer = parentScope;
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
        VariableDescription d = declarations.get(key);
        if (null != d)
            value = d.getType().convert(value);
        return values.put(key,value);
    }


    public Object put(VariableDescription var, Object value)
    {
        declarations.put(var.getName(), var);
        return put(var.getName(), value);
    }
}
