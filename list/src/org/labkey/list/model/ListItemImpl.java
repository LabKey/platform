/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.list.model;

import org.apache.log4j.Logger;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.util.GUID;

import java.util.HashMap;
import java.util.Map;

public class ListItemImpl implements ListItem
{
    boolean _new;
    ListDefinitionImpl _list;
    ListItm _itmOld;
    ListItm _itm;
    Map<String, ObjectProperty> _properties;
    Map<String, ObjectProperty> _oldProperties;
    private static final Logger _log = Logger.getLogger(ListItemImpl.class);

    public ListItemImpl(ListDefinitionImpl list, ListItm item)
    {
        _list = list;
        _itm = item;
    }

    public ListItemImpl(ListDefinitionImpl list)
    {
        _list = list;
        _itm = new ListItm();
        _itm.setEntityId(GUID.makeGUID());
        _itm.setListId(list.getListId());
        _new = true;
    }

    public Object getKey()
    {
        return _itm.getKey();
    }

    public void setKey(Object key)
    {
        edit().setKey(key);
    }

    public String getEntityId()
    {
        return _itm.getEntityId();
    }

    public void setEntityId(String entityId)
    {
        edit().setEntityId(entityId);
    }

    @Deprecated
    private OntologyObject getOntologyObject()
    {
        if (_itm.getObjectId() == null)
            return null;
        return OntologyManager.getOntologyObject(_itm.getObjectId());
    }

    private Map<String, ObjectProperty> ensureProperties()
    {
        if (_properties == null)
        {
            OntologyObject obj = getOntologyObject();

            _properties = new HashMap<>();

            if (obj != null)
            {
                Map<String,ObjectProperty> objProps = OntologyManager.getPropertyObjects(obj.getContainer(), obj.getObjectURI());
                for (Map.Entry<String,ObjectProperty> entry : objProps.entrySet())
                {
                    _properties.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return _properties;
    }

    private Map<String, ObjectProperty> editProperties()
    {
        if (_oldProperties == null)
        {
            _oldProperties = ensureProperties();
            _properties = new HashMap<>(_oldProperties);
        }
        return _properties;
    }

    public Object getProperty(DomainProperty property)
    {
        ObjectProperty prop = ensureProperties().get(property.getPropertyURI());

        return null != prop ? prop.value() : null;
    }

    public void setProperty(DomainProperty property, Object value)
    {
        ObjectProperty row = new ObjectProperty(null, property.getContainer(), property.getPropertyURI(), value, property.getPropertyDescriptor().getPropertyType(), property.getPropertyDescriptor().getName());
        editProperties().put(property.getPropertyURI(), row);
    }

    public Map<String, ObjectProperty> getProperties()
    {
        return ensureProperties();
    }

    private ListItm edit()
    {
        if (_new)
            return _itm;
        if (_itmOld == null)
        {
            _itmOld = _itm;
            _itm = _itmOld.clone();
        }
        return _itm;
    }
}
