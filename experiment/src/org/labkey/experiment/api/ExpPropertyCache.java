/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.experiment.api;

import org.apache.log4j.Logger;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.DbCache;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExperimentService;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ExpPropertyCache
{
    private static ExpPropertyCache instance = new ExpPropertyCache();
    private static final Logger _log = Logger.getLogger(ExpPropertyCache.class);

    public static ExpPropertyCache get()
    {
        return instance;
    }

    private String makeCacheKey(SQLFragment sql)
    {
        StringBuilder ret = new StringBuilder();
        ret.append(ExpPropertyCache.class.getName());
        ret.append("|||");
        ret.append(sql.toString());
        return ret.toString();
    }

    public PropertyDescriptor[] getPropertyDescriptors(TableInfo table, SQLFragment sql)
    {
        String key = makeCacheKey(sql);

        PropertyDescriptor[] pds;
        pds = (PropertyDescriptor[]) DbCache.get(table, key);
        if (pds != null)
            return pds;

        try
        {
            List<PropertyDescriptor> pdList = new ArrayList<PropertyDescriptor>();
            ResultSet rs = Table.executeQuery(ExperimentService.get().getSchema(), sql.getSQL(), sql.getParams().toArray());

            while (rs.next())
            {
                int propertyId = rs.getInt(1);
                PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(propertyId);
                pdList.add(pd);
            }

            pds = pdList.toArray(new PropertyDescriptor[pdList.size()]);
            rs.close();
        }
        catch (SQLException e)
        {
            _log.error("Error", e);
            return new PropertyDescriptor[0];
        }

        DbCache.put(table, key, pds, CacheManager.MINUTE * 30);
        return pds;
    }
}
