/*
 * Copyright (c) 2006-2007 LabKey Corporation
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

package org.labkey.query.controllers;

import org.labkey.api.query.QueryForm;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.util.UnexpectedException;
import org.labkey.query.data.Query;
import org.labkey.query.QueryDefinitionImpl;

import javax.servlet.ServletException;
import java.util.*;

public class TableInfoForm extends QueryForm
{
    public String getDataRegionName()
    {
        return null;
    }

    public boolean isDesign()
    {
        return getRequest().getParameter("design") != null;
    }

    public FieldKey[] getTableKeys()
    {
        String[] names = getRequest().getParameterValues("tableKey");
        if (names == null)
            return new FieldKey[0];
        FieldKey[] ret = new FieldKey[names.length];
        for (int i = 0; i < names.length; i ++)
        {
            ret[i] = FieldKey.fromString(names[i]);
        }
        return ret;
    }
    public FieldKey[] getFieldKeys()
    {
        String[] names = getRequest().getParameterValues("fieldKey");
        if (names == null)
            return new FieldKey[0];
        FieldKey[] ret = new FieldKey[names.length];
        for (int i = 0; i < names.length; i ++)
        {
            ret[i] = FieldKey.fromString(names[i]);
        }
        return ret;
    }


    public Map<FieldKey, TableInfo> getTableInfoMap()
    {
        Map<FieldKey, TableInfo> ret = new HashMap();
        if (!isDesign())
        {
            ret.put(null, getQueryDef().getTable(null, getSchema(), null));
        }
        else
        {
            Query query = ((QueryDefinitionImpl) getQueryDef()).getQuery(getSchema());
            for (FieldKey key : query.getFromTables())
            {
                ret.put(key, query.getFromTable(key));
            }
        }
        return ret;
    }

    public TableInfo getTableInfo(FieldKey target)
    {
        Map<FieldKey, TableInfo> map = getTableInfoMap();
        List<String> parts;
        if (target == null)
        {
            parts = Collections.EMPTY_LIST;
        }
        else
        {
            parts = target.getParts();
        }
        FieldKey current = null;
        TableInfo table = map.get(current);
        for (String part : parts)
        {
            current = new FieldKey(current, part);
            if (table == null)
            {
                table = map.get(current);
            }
            else
            {
                ColumnInfo column = table.getColumn(part);
                if (column == null)
                    return null;
                ForeignKey fk = column.getFk();
                if (fk == null)
                    return null;
                table = fk.getLookupTableInfo();
                if (table == null)
                    return null;
            }
        }
        return table;
    }
}
