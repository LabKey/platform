/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.study.plate;

import org.labkey.api.study.PropertySet;
import org.labkey.api.data.Container;

import java.util.*;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 4:43:49 PM
 */
public class PropertySetImpl implements PropertySet
{
    private Map<String, Object> _properties = new HashMap<>();
    private String _lsid;
    protected Container _container;

    public PropertySetImpl()
    {

    }

    public PropertySetImpl(Container container)
    {
        _container = container;
    }

    public Set<String> getPropertyNames()
    {
        return _properties.keySet();
    }


    public Object getProperty(String name)
    {
        return _properties.get(name);
    }

    public void setProperty(String name, Object value)
    {
        _properties.put(name, value);
    }

    public Map<String, Object> getProperties()
    {
        return Collections.unmodifiableMap(_properties);
    }
    
    public String getLSID()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        _lsid = lsid;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }
}
