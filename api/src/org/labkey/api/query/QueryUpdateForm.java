/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

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

    public QueryUpdateForm(TableInfo table, ViewContext ctx)
    {
        this(table, ctx, null);
    }

    public QueryUpdateForm(TableInfo table, ViewContext ctx, BindException errors)
    {
        _tinfo = table;
        _dynaClass = new QueryWrapperDynaClass(this);
        setViewContext(ctx);

        // TODO: Fix this hack.
        // This should be a normal form that uses normal Spring parameter binding
        BindException newErrors = bindParameters(ctx.getBindPropertyValues());

        // More hackiness -- can only add more errors if object names match.  Blow up in dev mode, ignore in production.
        if (newErrors.hasErrors() && null != errors)
        {
            assert newErrors.getObjectName().equals(errors.getObjectName());

            if (newErrors.getObjectName().equals(errors.getObjectName()))
                errors.addAllErrors(newErrors);
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
        // 6962 : ExternalSchema update problems on PostgreSQL 8.3
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
