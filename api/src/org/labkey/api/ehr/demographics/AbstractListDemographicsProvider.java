/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.api.ehr.demographics;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.Results;
import org.labkey.api.module.Module;
import org.labkey.api.query.FieldKey;

import java.sql.Clob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * User: bimber
 * Date: 7/9/13
 * Time: 9:42 PM
 */
abstract public class AbstractListDemographicsProvider extends AbstractDemographicsProvider
{
    protected String _propName;

    public AbstractListDemographicsProvider(Module owner, String schemaName, String queryName, String propName)
    {
        super(owner, schemaName, queryName);
        _propName = propName;
    }

    public String getName()
    {
        return _propName;
    }

    @Override
    protected void processRow(Results rs, Map<FieldKey, ColumnInfo> cols, Map<String, Object> map) throws SQLException
    {
        if (map.containsKey(_propName) && !(map.get(_propName) instanceof List))
        {
            _log.warn("Demographics record already has a value for " + _propName + " that is not a list");
            map.put(_propName, null);
        }

        List<Map<String, Object>> records = (List)map.get(_propName);
        if (records == null)
            records = new ArrayList<>();

        Map<String, Object> record = new TreeMap<>();
        for  (FieldKey key : cols.keySet())
        {
            if ("Id".equalsIgnoreCase(key.toString()))
                continue;

            Object val = rs.getObject(key);
            if (val instanceof Clob)
            {
                val = ConvertHelper.convertClobToString((Clob)val);
            }

            record.put(key.toString(), val);
        }
        records.add(record);

        //NOTE: records are ordered to allow validation
        if (getSort() == null)
        {
            Collections.sort(records, new Comparator<Map<String, Object>>()
            {
                @Override
                public int compare(Map<String, Object> o1, Map<String, Object> o2)
                {
                    if (o1 == null)
                        return -1;
                    else if (o2 == null)
                        return 1;

                    return o1.toString().compareTo(o2.toString());
                }
            });
        }

        map.put(_propName, records);
    }

    @Override
    public Set<String> getKeys()
    {
        return Collections.singleton(_propName);
    }

    @Override
    public Collection<String> getKeysToTest()
    {
        return getKeys();
    }
}
