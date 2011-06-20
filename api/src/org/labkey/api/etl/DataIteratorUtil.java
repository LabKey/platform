/*
 * Copyright (c) 2011 LabKey Corporation
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

package org.labkey.api.etl;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.springframework.validation.BindingResult;
import org.springframework.validation.MapBindingResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: 2011-05-31
 * Time: 12:52 PM
 */
public class DataIteratorUtil
{
    public static Map<String,Integer> createColumnNameMap(DataIterator di)
    {
        Map<String,Integer> map = new CaseInsensitiveHashMap<Integer>();
        for (int i=1 ; i<=di.getColumnCount() ; ++i)
        {
            map.put(di.getColumnInfo(i).getName(),i);
        }
        return map;
    }


    public static Map<String,Integer> createColumnAndPropertyMap(DataIterator di)
    {
        Map<String,Integer> map = new CaseInsensitiveHashMap<Integer>();
        for (int i=1 ; i<=di.getColumnCount() ; ++i)
        {
            ColumnInfo col = di.getColumnInfo(i);
            map.put(col.getName(),i);
            String prop = col.getPropertyURI();
            if (null != prop && !col.isMvIndicatorColumn() && !col.isRawValueColumn())
            {
                if (!map.containsKey(prop))
                    map.put(prop, i);
            }
        }
        return map;
    }


    public static Map<String,ColumnInfo> createAllAliasesMap(TableInfo target)
    {
        List<ColumnInfo> cols = target.getColumns();
        Map<String, ColumnInfo> targetAliasesMap = new CaseInsensitiveHashMap<ColumnInfo>(cols.size()*4);
        for (ColumnInfo col : cols)
        {
            if (col.isMvIndicatorColumn() || col.isRawValueColumn())
                continue;
            String name = col.getName();
            targetAliasesMap.put(name, col);
            String uri = col.getPropertyURI();
            if (null != uri)
            {
                if (!targetAliasesMap.containsKey(uri))
                    targetAliasesMap.put(uri, col);
                String propName = uri.substring(uri.lastIndexOf('#')+1);
                if (!targetAliasesMap.containsKey(propName))
                    targetAliasesMap.put(propName,col);
            }
            String label = col.getLabel();
            if (null != label && !targetAliasesMap.containsKey(label))
                targetAliasesMap.put(label, col);
            for (String alias : col.getImportAliasSet())
                if (!targetAliasesMap.containsKey(alias))
                    targetAliasesMap.put(alias, col);
        }
        return targetAliasesMap;
    }


    /* NOTE doesn't check column mapping collisions */
    public static ArrayList<ColumnInfo> matchColumns(DataIterator input, TableInfo target)
    {
        Map<String,ColumnInfo> targetMap = createAllAliasesMap(target);
        ArrayList<ColumnInfo> matches = new ArrayList<ColumnInfo>(input.getColumnCount()+1);
        matches.add(null);
        
        // match columns to target columninfos (duplicates StandardETL, extract shared method?)
        for (int i=1 ; i<=input.getColumnCount() ; i++)
        {
            ColumnInfo from = input.getColumnInfo(i);
            if (from.getName().toLowerCase().endsWith(MvColumn.MV_INDICATOR_SUFFIX.toLowerCase()))
            {
                matches.add(null);
                continue;
            }
            ColumnInfo to = null;
            if (null != from.getPropertyURI())
                to = targetMap.get(from.getPropertyURI());
            if (null == to)
                to = targetMap.get(from.getName());
            matches.add(to);
        }
        return matches;
    }


    static DataIterator makeScrollable(DataIterator di)
    {
        return CachingDataIterator.wrap(di);
    }
}
