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

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ParameterDescription;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.property.SystemProperty;
import org.labkey.di.data.TransformProperty;

import java.sql.Timestamp;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import static org.labkey.di.VariableMap.Scope.*;


/**
 * User: matthew
 * Date: 4/22/13
 * Time: 11:44 AM
 */
public class VariableMapImpl implements VariableMap
{
    final VariableMap _outer;
    final CaseInsensitiveHashMap<ParameterDescription> declarations = new CaseInsensitiveHashMap<>();
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
                put(property.getName(), property.value(), local);
            }
        }
    }

    public VariableMapImpl(VariableMap parentScope, JSONObject json)
    {
        _outer = parentScope;
        if (json != null)
        {
            for (Map.Entry<String, Object> e : json.entrySet())
            {
                put(e.getKey(), e.getValue());
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
        return put(key, value, local);
    }


    @Override
    public Object put(String key, Object value, Enum scope)
    {
        if (local == scope || null == _outer)
        {
            ParameterDescription pd = declarations.get(key);
            if (null != pd)
                value = pd.getJdbcType().convert(value);
            return values.put(key,value);
        }
        else
        {
            declarations.remove(key);
            values.remove(key);
            if (Scope.parent == scope)
                scope = local;
            return _outer.put(key, value, scope);
        }
    }



    @Override
    public Object put(ParameterDescription prop, Object value)
    {
        return put(prop, value, local);
    }


    public Object put(ParameterDescription prop, Object value, Enum scope)
    {
        if (local == scope || null == _outer)
        {
            declarations.put(prop.getName(), prop);
            return put(prop.getName(), value, scope);
        }
        else
        {
            declarations.remove(prop.getName());
            values.remove(prop.getName());
            if (Scope.parent == scope)
                scope = local;
            return _outer.put(prop, value, scope);
        }
    }


    @Override
    public Set<String> keySet()
    {
        // consider: adding _outer keyset values as well
        return values.keySet();
    }


    @Override
    public ParameterDescription getDescriptor(String key)
    {
        if (null == _outer || declarations.containsKey(key))
            return declarations.get(key);
        return _outer.getDescriptor(key);
    }


    public JSONObject toJSONObject()
    {
        JSONObject j = new JSONObject();
        for (String key : keySet())
        {
            Object value = get(key);
            // we need full fidelity round trip, so don't use regular json format
            if (value instanceof java.sql.Timestamp)
                value = value.toString();
            else if (value instanceof Date)
                value = new Timestamp(((Date)value).getTime()).toString();
            j.put(key, value);
        }
        return j;
    }


    public static class TestCase extends Assert
    {
        @Test
        public void variableMap()
        {
            final int recordsModified = 10;
            final int recordsInserted1 = 2;
            final int recordsInserted2 = 3;
            final int recordDeleted = 6;
            final Date startTimestamp = new Date();
            final Date endTimestamp  = new Date(startTimestamp.getTime() + 60000 * 3); // 3 minutes later
            final String greeting1 = "Hello";
            final String greetingKey1 = "Greeting1";
            final String greeting2 = "Howdy";
            final String greetingKey2 = "Greeting2";

            // ensure no arg ctor is available
            VariableMap vmap = new VariableMapImpl();

            // test that our known system properties are available
            vmap.put(TransformProperty.IncrementalStartTimestamp.getPropertyDescriptor(), startTimestamp, local);
            vmap.put(TransformProperty.IncrementalEndTimestamp.getPropertyDescriptor(), endTimestamp, local);
            vmap.put(TransformProperty.RecordsDeleted.getPropertyDescriptor(), recordDeleted, local);
            vmap.put(TransformProperty.RecordsInserted.getPropertyDescriptor(), recordsInserted1, local);
            vmap.put(TransformProperty.RecordsModified.getPropertyDescriptor(), recordsModified, local);

            // spot check  that a variable added as a system property is put in both
            // the declarations and values maps using the property descriptor's name
            verifyProp(vmap, TransformProperty.RecordsInserted, recordsInserted1);
            assert (TransformProperty.IncrementalEndTimestamp.getPropertyDescriptor() ==
                    vmap.getDescriptor(TransformProperty.IncrementalEndTimestamp.getPropertyDescriptor().getName()));

            // add a non-system property
            // verify that this "transient" property is not in the descriptors map
            vmap.put(greetingKey1, greeting1, local);
            vmap.put(greetingKey2, greeting2, local);
            verifyProp(vmap, greetingKey1, greeting1);
            assert (null == vmap.getDescriptor(greetingKey1));

            // verify inheritance scenarios
            VariableMap vmapChild = new VariableMapImpl(vmap);

            // ensure child system prop overrides parent prop when accessed through child map
            // but doesn't affect parent map value
            vmapChild.put(TransformProperty.RecordsInserted.getPropertyDescriptor(), recordsInserted2, local);
            verifyProp(vmapChild, TransformProperty.RecordsInserted, recordsInserted2);
            verifyProp(vmap, TransformProperty.RecordsInserted, recordsInserted1);

            // ditto for non-system props
            vmapChild.put(greetingKey1, greeting2, local);
            verifyProp(vmapChild, greetingKey1, greeting2);
            verifyProp(vmap, greetingKey1, greeting1);

            // verify we can access inherited properties from the parent that
            // are not in the child
            verifyProp(vmapChild, greetingKey2, greeting2);
            verifyProp(vmapChild, TransformProperty.RecordsDeleted, recordDeleted);
            assert(TransformProperty.IncrementalStartTimestamp.getPropertyDescriptor() ==
                   vmapChild.getDescriptor(TransformProperty.IncrementalStartTimestamp.getPropertyDescriptor().getName()));
        }

        private void verifyProp(VariableMap map, SystemProperty p, Object v)
        {
            assert (v == map.get(p.getPropertyDescriptor().getName()));
        }

        private void verifyProp(VariableMap map, String k, Object v)
        {
            assert (v == map.get(k));
        }
    }
}