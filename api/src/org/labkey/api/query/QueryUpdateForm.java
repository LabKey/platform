/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableViewForm;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Extension of the class TableViewForm which deals with the fact that we don't have much control over the names
 * of fields in a user-defined table.
 * All column names are prefixed by "quf_" to generate the name of the field on the input form.
 */
public class QueryUpdateForm extends TableViewForm
{
    /**
     * Prefix prepended to all form elements
     */
    public static final String PREFIX = "quf_";

    public QueryUpdateForm(TableInfo table, HttpServletRequest request)
    {
        this(table, request, Collections.<String,String>emptyMap());
    }

    public QueryUpdateForm(TableInfo table, HttpServletRequest request, Map<String,String> extraParameters)
    {
        _tinfo = table;
        _dynaClass = new QueryWrapperDynaClass(this);
        reset(null, request);
        for (Enumeration en = request.getParameterNames(); en.hasMoreElements();)
        {
            String key = (String) en.nextElement();

            for (String value : request.getParameterValues(key))
            {
                set(key, value);
            }
        }
        for (Map.Entry<String,String> entry : extraParameters.entrySet())
        {
            set(entry.getKey(), entry.getValue());
        }
    }

    public ColumnInfo getColumnByFormFieldName(String name)
    {
        if (name.length() < PREFIX.length())
            return null;
        return getTable().getColumn(name.substring(PREFIX.length()));
//        ColumnInfo col = getTable().getColumn(name);
//        if (col == null && name.length() > PREFIX.length())
//            col = getTable().getColumn(name.substring(PREFIX.length()));
//        return col;
    }

    public String getFormFieldName(ColumnInfo column)
    {
        // 6962 : DbUserSchema update problems on PostgreSQL 8.3
        // Some controllers depend on this form's TableViewForm.getPkNamesList() matching the
        // primary key column names.
//        if (column.isKeyField())
//            return column.getName();
        return PREFIX + column.getName();
    }

    public Map<String,Object> getDataMap()
    {
        Map<String,Object> data = new HashMap<String,Object>();
        for (Map.Entry<String,Object> entry : getTypedValues().entrySet())
        {
            String key = entry.getKey();
            if (key.startsWith(PREFIX))
            {
                key = key.substring(4);
                data.put(key, entry.getValue());
            }
        }
        return data;
    }
}
