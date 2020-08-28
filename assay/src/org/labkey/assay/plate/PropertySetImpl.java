/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.assay.plate;

import org.labkey.api.data.Container;
import org.labkey.api.study.PropertySet;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 4:43:49 PM
 */
public abstract class PropertySetImpl implements PropertySet
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

    @Override
    public Set<String> getPropertyNames()
    {
        return _properties.keySet();
    }


    @Override
    public Object getProperty(String name)
    {
        return _properties.get(name);
    }

    @Override
    public void setProperty(String name, Object value)
    {
        _properties.put(name, value);
    }

    @Override
    public void setProperties(Map<String, Object> properties)
    {
        _properties = new HashMap<>(properties);
    }

    public Map<String, Object> getProperties()
    {
        return Collections.unmodifiableMap(_properties);
    }

    @Override
    public String getLSID()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        _lsid = lsid;
    }

    @Override
    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }
}
