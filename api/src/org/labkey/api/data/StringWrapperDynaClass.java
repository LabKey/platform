/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import org.apache.commons.beanutils.DynaClass;
import org.apache.commons.beanutils.DynaProperty;
import org.labkey.api.collections.CaseInsensitiveHashMap;

public abstract class StringWrapperDynaClass implements DynaClass
{
    protected DynaProperty[] _dynaProps;
    protected Map<String, DynaProperty> _dynaPropMap;
    protected Map<String, Class> _propTypes;
    protected String _name;

    /**
     * Creates a basic wrapper dynaclass where all of the properties
     * are strings. WARNING: currently does not deal with nested classes,
     * mapped classes or arrays. Just simple property types.
     * <p/>
     * NOTE: Extended classes MUST call init before returning from
     * their constructor
     */
    protected StringWrapperDynaClass()
    {
    }

    /**
     * Initializes a dynaclass
     *
     * @param className Class name for this dynaClass
     * @param propTypes Maps prop names to Class objects for the true class of each object.
     */
    protected void init(String className, Map<String, Class> propTypes)
    {
        _propTypes = propTypes;
        _name = className;
        _dynaPropMap = new CaseInsensitiveHashMap<>();
        ArrayList<DynaProperty> dynaPropList = new ArrayList<>(propTypes.size());
        Set<String> keySet = propTypes.keySet();
        for (String key : keySet)
        {
            if ("class".equals(key))
                continue;
            Class propClass = propTypes.get(key);

            DynaProperty dynaProp = new DynaProperty(key, String.class);
            dynaPropList.add(dynaProp);
            _dynaPropMap.put(key, dynaProp);
        }
        _dynaProps = dynaPropList.toArray(new DynaProperty[dynaPropList.size()]);
    }

    public DynaProperty[] getDynaProperties()
    {
        return _dynaProps;
    }

    public DynaProperty getDynaProperty(String arg0)
    {
        return _dynaPropMap.get(arg0);
    }

    public Class getTruePropType(String propName)
    {
        return _propTypes.get(propName);
    }

    public String getName()
    {
        return _name;
    }

    /**
     * Return a caption for this property. Base implemententation
     * returns its input. CONSIDER: generate a friendly-ized name
     */
    public String getPropertyCaption(String propName)
    {
        return propName;
    }

} 
