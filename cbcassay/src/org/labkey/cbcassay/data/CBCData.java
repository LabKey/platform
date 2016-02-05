/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AbstractTsvAssayProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.collections.CaseInsensitiveHashMap;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
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
        AssayProvider provider = AssayService.get().getProvider(protocol);
        List<FieldKey> fieldKeys = new ArrayList<>();
        Domain dataDomain = provider.getResultsDomain(protocol);
        for (DomainProperty property : dataDomain.getProperties())
        {
            fieldKeys.add(FieldKey.fromParts(property.getName()));
        }
        TableInfo tableInfo = provider.createProtocolSchema(user, data.getContainer(), protocol, null).createDataTable();
        Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(tableInfo, fieldKeys);
        assert columns.size() == fieldKeys.size() : "Missing a column for at least one of the properties";
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME), objectId);

        Map<String, Object> map = new TableSelector(tableInfo, columns.values(), filter, null).getMap();

        if (null == map)
            return null;

        // fetch metadata using the column PropertyDescriptors
        Map<String, CBCDataProperty> meta = new CaseInsensitiveHashMap<>();
        Map<String, Object> values = new HashMap<>();

        for (ColumnInfo column : columns.values())
        {
            DomainProperty property = dataDomain.getPropertyByName(column.getName());
            // XXX: check it has min/max/units properties
            meta.put(property.getName(), new CBCDataProperty(property.getPropertyDescriptor()));
            Object value = column.getValue(map);
            values.put(property.getName(), value);
            values.put(property.getPropertyURI(), value);
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
