/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

package org.labkey.cbcassay.data;

import org.labkey.api.data.*;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.view.HttpView;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.cbcassay.CBCAssayManager;
import org.labkey.cbcassay.CBCAssayProvider;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class CBCData
{
    private ExpData _expData;
    private ExpProtocol _expProtocol;
    private Map<String, Object> _values;
    private Map<String, CBCDataProperty> _meta;

    public CBCData(ExpData data, ExpProtocol protocol, Map<String, Object> values, Map<String, CBCDataProperty> meta)
    {
        _expData = data;
        _expProtocol = protocol;
        _values = values;
        _meta = meta;
    }

    public static CBCData fromObjectId(int objectId, ExpData data, ExpProtocol protocol, User user) throws SQLException
    {
        // fetch row data
        OntologyObject oo = OntologyManager.getOntologyObject(objectId);
        Map<String, Object> values = OntologyManager.getProperties(data.getContainer(), oo.getObjectURI());

        // fetch metadata using the column PropertyDescriptors
        String dataDomainUri = CBCAssayProvider.getDomainURIForPrefix(protocol, ExpProtocol.ASSAY_DOMAIN_DATA);
        PropertyDescriptor[] properties = OntologyManager.getPropertiesForType(dataDomainUri, data.getContainer());
        Map<String, CBCDataProperty> meta = new CaseInsensitiveHashMap<CBCDataProperty>(properties.length*2);
        for (PropertyDescriptor property : properties)
        {
            // XXX: check it has min/max/units properties
            meta.put(property.getName(), new CBCDataProperty(property));
            values.put(property.getName(), values.get(property.getPropertyURI()));
        }

        return new CBCData(data, protocol, values, meta);
    }

    public ExpData getExpData()
    {
        return _expData;
    }

    public ExpProtocol getExpProtocol()
    {
        return _expProtocol;
    }
    
    public Container getContainer()
    {
        return getExpData().getContainer();
    }

    public String getLSID()
    {
        return getExpData().getLSID();
    }

    public ExpRun getRun()
    {
        ExpRun run = getExpData().getRun();
        // XXX: wrap in CBCRun data object
        return run;
    }

    public int getRowId()
    {
        return getExpData().getRowId();
    }

    public String getName()
    {
        return getExpData().getName();
    }

    public String getComment()
    {
        return getExpData().getComment();
    }

    public String getLabel(String name)
    {
        CBCDataProperty prop = _meta.get(name);
        if (prop != null)
        {
            String label = prop.getPropertyDescriptor().getLabel(); 
            if (label != null)
                return label;
        }
        return name;
    }

    public Object getValue(String name)
    {
        return _values.get(name);
    }

    public boolean inRange(String name)
    {
        CBCDataProperty prop = _meta.get(name);
        if (prop != null)
        {
            return prop.inRange((Double) getValue(name));
        }
        return true;
    }

    public Double getMinValue(String name)
    {
        CBCDataProperty prop = _meta.get(name);
        if (prop != null)
            return prop.getMinValue();
        return null;
    }

    public Double getMaxValue(String name)
    {
        CBCDataProperty prop = _meta.get(name);
        if (prop != null)
            return prop.getMaxValue();
        return null;
    }

    public String getUnits(String name)
    {
        CBCDataProperty prop = _meta.get(name);
        if (prop != null)
            return prop.getUnits();
        return null;
    }

    public String getSampleId()
    {
        String id = (String) getValue("SampleId");
        if (id == null)
            return "<none>";
        return id;
    }

    public String getSequence()
    {
        return (String) getValue("Sequence");
    }

    public Date getDate()
    {
        return (Date) getValue("Date");
    }

}
