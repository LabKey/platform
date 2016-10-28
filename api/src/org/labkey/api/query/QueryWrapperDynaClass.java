/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.StringWrapperDynaClass;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.apache.commons.beanutils.DynaBean;
import org.labkey.api.data.TableInfo;

import java.lang.reflect.Array;
import java.util.Map;

public class QueryWrapperDynaClass extends StringWrapperDynaClass
{
    QueryUpdateForm _form;

    public QueryWrapperDynaClass(@NotNull QueryUpdateForm form)
    {
        _form = form;
        TableInfo table = form.getTable();
        if (table == null)
            throw new IllegalArgumentException();

        // CONSIDER: Handle MultiValueFK in column.getJavaClass() directly
        Map<String, Class> propMap = new CaseInsensitiveHashMap<>();
        for (ColumnInfo column : table.getColumns())
        {
            boolean multiValued = column.getFk() instanceof MultiValuedForeignKey && ((MultiValuedForeignKey)column.getFk()).isMultiSelectInput();
            if (multiValued)
                propMap.put(_form.getFormFieldName(column), arrayClass(column.getJavaClass()));
            else
                propMap.put(_form.getFormFieldName(column), column.getJavaClass());
        }

        init("className", propMap);

    }

    private static <K> Class<?> arrayClass(Class<K> k)
    {
        Object o = Array.newInstance(k, 0);
        return o.getClass();
    }
    

    public DynaBean newInstance() throws IllegalAccessException, InstantiationException
    {
        throw new UnsupportedOperationException();
    }

    public String getPropertyCaption(String propName)
    {
        ColumnInfo column = _form.getColumnByFormFieldName(propName);
        if (column == null)
            return propName;
        return column.getLabel();
    }
}
