/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;

import org.apache.commons.beanutils.DynaBean;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates a DynaClass for a table where all of the properties are
 * strings.
 */
public class TableWrapperDynaClass extends StringWrapperDynaClass
{
    private TableInfo _tinfo;
    private static final Map<TableInfo, TableWrapperDynaClass> _dynClasses = new ConcurrentHashMap<>();

    private TableWrapperDynaClass(TableInfo tinfo)
    {
        _tinfo = tinfo;
        List<ColumnInfo> cols = tinfo.getColumns();
        Map<String, Class> propMap = new CaseInsensitiveHashMap<>();
        for (ColumnInfo col : cols)
            propMap.put(col.getPropertyName(), col.getJavaClass());

        init(tinfo.getName(), propMap);
    }

    public static TableWrapperDynaClass getDynaClassInstance(@NotNull TableInfo tinfo)
    {
        TableWrapperDynaClass tdc = _dynClasses.get(tinfo);
        if (null == tdc)
        {
            tdc = new TableWrapperDynaClass(tinfo);
            if (tinfo instanceof SchemaTableInfo)
                _dynClasses.put(tinfo, tdc);
        }
        return tdc;
    }

    public TableInfo getTable()
    {
        return _tinfo;
    }

    public String getName()
    {
        return _tinfo.getName();
    }

    public DynaBean newInstance() throws IllegalAccessException, InstantiationException
    {
        return new TableViewForm(_tinfo);
    }
} 
