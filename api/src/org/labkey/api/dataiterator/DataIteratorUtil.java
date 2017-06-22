/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

package org.labkey.api.dataiterator;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderService;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * User: matthewb
 * Date: 2011-05-31
 * Time: 12:52 PM
 */
public class DataIteratorUtil
{
    public static Map<FieldKey, ColumnInfo> createFieldKeyMap(DataIterator di)
    {
        Map<FieldKey, ColumnInfo> map = new LinkedHashMap<>();
        for (int i=1 ; i<=di.getColumnCount() ; ++i)
        {
            ColumnInfo col = di.getColumnInfo(i);
            map.put(col.getFieldKey(), col);
        }
        return map;
    }

    public static Map<FieldKey, Integer> createFieldIndexMap(DataIterator di)
    {
        Map<FieldKey, Integer> map = new LinkedHashMap<>();
        for (int i=1 ; i<=di.getColumnCount() ; ++i)
        {
            ColumnInfo col = di.getColumnInfo(i);
            map.put(col.getFieldKey(), i);
        }
        return map;
    }

    public static Map<String,Integer> createColumnNameMap(DataIterator di)
    {
        Map<String,Integer> map = new CaseInsensitiveHashMap<>();
        for (int i=1 ; i<=di.getColumnCount() ; ++i)
        {
            map.put(di.getColumnInfo(i).getName(),i);
        }
        return map;
    }


    public static Map<String,Integer> createColumnAndPropertyMap(DataIterator di)
    {
        Map<String,Integer> map = new CaseInsensitiveHashMap<>();
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


    public static Map<String,ColumnInfo> createTableMap(TableInfo target, boolean useImportAliases)
    {
        List<ColumnInfo> cols = target.getColumns();
        Map<String, ColumnInfo> targetAliasesMap = new CaseInsensitiveHashMap<>(cols.size()*4);
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
            if (useImportAliases)
            {
                for (String alias : col.getImportAliasSet())
                    if (!targetAliasesMap.containsKey(alias))
                        targetAliasesMap.put(alias, col);
            }

            // Issue 21015: Dataset snapshot over flow assay dataset doesn't pick up stat column values
            // TSVColumnWriter.ColumnHeaderType.queryColumnName format is a FieldKey display value from the column name. Blech.
            String tsvQueryColumnName = FieldKey.fromString(name).toDisplayString();
            if (!targetAliasesMap.containsKey(tsvQueryColumnName))
                targetAliasesMap.put(tsvQueryColumnName, col);
        }
        return targetAliasesMap;
    }

    enum MatchType {propertyuri, name, alias, jdbcname}

    protected static Map<String,Pair<ColumnInfo,MatchType>> _createTableMap(TableInfo target, boolean useImportAliases)
    {
        List<ColumnInfo> cols = target.getColumns();
        Map<String, Pair<ColumnInfo,MatchType>> targetAliasesMap = new CaseInsensitiveHashMap<>(cols.size()*4);
        for (ColumnInfo col : cols)
        {
            if (col.isMvIndicatorColumn() || col.isRawValueColumn())
                continue;
            String name = col.getName();
            targetAliasesMap.put(name, new Pair<>(col,MatchType.name));
            String uri = col.getPropertyURI();
            if (null != uri)
            {
                if (!targetAliasesMap.containsKey(uri))
                    targetAliasesMap.put(uri, new Pair<>(col, MatchType.propertyuri));
                String propName = uri.substring(uri.lastIndexOf('#')+1);
                if (!targetAliasesMap.containsKey(propName))
                    targetAliasesMap.put(propName, new Pair<>(col, MatchType.alias));
            }
            String label = col.getLabel();
            if (null != label && !targetAliasesMap.containsKey(label))
                targetAliasesMap.put(label, new Pair<>(col, MatchType.alias));
            String translatedFieldKey;
            if (useImportAliases)
            {
                for (String alias : col.getImportAliasSet())
                    if (!targetAliasesMap.containsKey(alias))
                        targetAliasesMap.put(alias, new Pair<>(col, MatchType.alias));
                // Be sure we have an alias the column name we generate for TSV exports. See issue 21774
                translatedFieldKey = FieldKey.fromString(col.getName()).toDisplayString();
                if (!targetAliasesMap.containsKey(translatedFieldKey))
                {
                    targetAliasesMap.put(translatedFieldKey, new Pair<>(col, MatchType.alias));
                }
            }
            // Jdbc resultset names have substitutions for special characters. If this is such a column, need the substituted name to match on
            translatedFieldKey = col.getJdbcRsName();
            if (!name.equals(translatedFieldKey))
            {
                targetAliasesMap.put(translatedFieldKey, new Pair<>(col, MatchType.jdbcname));
            }
        }
        return targetAliasesMap;
    }


    /* NOTE doesn't check column mapping collisions */
    protected static ArrayList<Pair<ColumnInfo,MatchType>> _matchColumns(DataIterator input, TableInfo target, boolean useImportAliases)
    {
        Map<String,Pair<ColumnInfo,MatchType>> targetMap = _createTableMap(target, useImportAliases);
        ArrayList<Pair<ColumnInfo,MatchType>> matches = new ArrayList<>(input.getColumnCount()+1);
        matches.add(null);

        // match columns to target columninfos (duplicates StandardDataIteratorBuilder, extract shared method?)
        for (int i=1 ; i<=input.getColumnCount() ; i++)
        {
            ColumnInfo from = input.getColumnInfo(i);
            if (from.getName().toLowerCase().endsWith(MvColumn.MV_INDICATOR_SUFFIX.toLowerCase()))
            {
                matches.add(null);
                continue;
            }
            Pair<ColumnInfo,MatchType> to = null;
            if (null != from.getPropertyURI())
                to = targetMap.get(from.getPropertyURI());
            if (null == to)
                to = targetMap.get(from.getName());
            matches.add(to);
        }
        return matches;
    }


    /** throws ValidationException only if there are unresolvable ambiguity in the source->destination column mapping */
    public static ArrayList<ColumnInfo> matchColumns(DataIterator input, TableInfo target, boolean useImportAliases, ValidationException setupError)
    {
        ArrayList<Pair<ColumnInfo,MatchType>> matches = _matchColumns(input, target, useImportAliases);
        MultiValuedMap<FieldKey,Integer> duplicatesMap = new ArrayListValuedHashMap<>(input.getColumnCount()+1);

        for (int i=1 ; i<= input.getColumnCount() ; i++)
        {
            Pair<ColumnInfo,MatchType> match = matches.get(i);
            if (null != match)
                duplicatesMap.put(match.first.getFieldKey(),i);
        }

        // handle duplicates, by priority
        for (Map.Entry<FieldKey, Collection<Integer>> e : duplicatesMap.asMap().entrySet())
        {
            if (e.getValue().size() == 1)
                continue;
            int[] counts = new int[MatchType.values().length];
            for (Integer i : e.getValue())
                counts[matches.get(i).second.ordinal()]++;
            for (MatchType mt : MatchType.values())
            {
                int count = counts[mt.ordinal()];

                if (count == 1)
                {
                    // found the best match
                    for (Integer i : e.getValue())
                    {
                        if (matches.get(i).second != mt)
                            matches.set(i, null);
                    }
                    break;
                }
                if (count > 1)
                {
                    setupError.addGlobalError("Two columns mapped to target column: " + e.getKey().toString());
                    break;
                }
            }
        }

        ArrayList<ColumnInfo> ret = new ArrayList<>(matches.size());
        for (Pair<ColumnInfo,MatchType> m : matches)
            ret.add(null==m ? null : m.first);
        return ret;
    }




    // NOTE: first consider if using QueryUpdateService is better
    // this is just a point-to-point copy _without_ triggers
    public static int copy(File from, TableInfo to, Container c, User user) throws IOException, BatchValidationException
    {

        BatchValidationException errors = new BatchValidationException();
        DataIteratorContext context = new DataIteratorContext(errors);
        DataLoader loader = DataLoaderService.get().createLoader(from, null, true, c, TabLoader.TSV_FILE_TYPE);
        loader.setInferTypes(false);
        int count = copy(context, loader, to, c, user);
        if (errors.hasErrors())
            throw errors;
        return count;
    }


    public static int copy(DataIteratorContext context, DataIterator from, TableInfo to, Container c, User user) throws IOException, BatchValidationException
    {
        DataIteratorBuilder builder = new DataIteratorBuilder.Wrapper(from);
        return copy(context, builder, to, c, user);
    }

    // NOTE: first consider if using QueryUpdateService is better
    // this is just a point-to-point copy _without_ triggers
    public static int merge(DataIteratorBuilder from, TableInfo to, Container c, User user) throws IOException, BatchValidationException
    {
        BatchValidationException errors = new BatchValidationException();
        DataIteratorContext context = new DataIteratorContext(errors);
        context.setInsertOption(QueryUpdateService.InsertOption.MERGE);
        context.setSupportAutoIncrementKey(true);
        int count = copy(context, from, to, c, user);
        if (errors.hasErrors())
            throw errors;
        return count;
    }


    // NOTE: first consider if using QueryUpdateService is better
    // this is just a point-to-point copy _without_ triggers
    public static int copy(DataIteratorContext context, DataIteratorBuilder from, TableInfo to, Container c, User user) throws IOException, BatchValidationException
    {
        StandardDataIteratorBuilder etl = StandardDataIteratorBuilder.forInsert(to, from, c, user, context);
        DataIteratorBuilder insert = ((UpdateableTableInfo)to).persistRows(etl, context);
        Pump pump = new Pump(insert, context);
        pump.run();
        return pump.getRowCount();
    }


    public static void closeQuietly(DataIterator it)
    {
        if (null == it)
            return;
        try
        {
            it.close();
        }
        catch (Exception x)
        {
            Logger.getLogger(it.getClass()).warn("Unexpected error closing DataIterator", x);
        }
    }


    /*
     * Wrapping functions to add functionality to existing DataIterators
     */

    public static ScrollableDataIterator wrapScrollable(DataIterator di)
    {
        return CachingDataIterator.wrap(di);
    }


    public static MapDataIterator wrapMap(DataIterator in, boolean mutable)
    {
        if (!mutable && in instanceof MapDataIterator && ((MapDataIterator)in).supportsGetMap())
        {
            return (MapDataIterator)in;
        }
        return new MapDataIterator.MapDataIteratorImpl(in, mutable);
    }


    public static class DataSpliterator implements Spliterator<Map<String,Object>>
    {
        private final MapDataIterator maps;

        DataSpliterator(MapDataIterator maps)
        {
            this.maps = maps;
        }

        @Override
        public boolean tryAdvance(Consumer<? super Map<String, Object>> action)
        {
            try
            {
                if (!maps.next())
                    return false;
                Map<String,Object> m = maps.getMap();
                action.accept(m);
                return true;
            }
            catch (BatchValidationException x)
            {
                return false;
            }
        }

        @Override
        public Spliterator<Map<String, Object>> trySplit()
        {
            return null;
        }

        @Override
        public long estimateSize()
        {
            return maps.estimateSize();
        }

        @Override
        public int characteristics()
        {
            return 0;
        }
    }


    public static Stream<Map<String,Object>> stream(DataIterator in, boolean mutable)
    {
        final MapDataIterator maps = wrapMap(in,mutable);
        Stream<Map<String,Object>> s = StreamSupport.stream(new DataSpliterator(maps),false);
        s.onClose(() -> {
            try
            {
                maps.close();
            }
            catch (IOException x)
            {
                /* */
            }
        });
        return s;
    }


    public static class TestCase extends Assert
    {
        String csv = "a,b,c\n1,2,3\n4,5,6\n";

        DataIterator data() throws Exception
        {
            DataLoader dl = new TabLoader.CsvFactory().createLoader(IOUtils.toInputStream(csv, StringUtilsLabKey.DEFAULT_CHARSET),true);
            DataIteratorContext c = new DataIteratorContext();
            return dl.getDataIterator(c);
        }
        @Test
        public void testStream() throws Exception
        {
            assertEquals(2, data().stream().count());
            assertEquals(5, data().stream().mapToInt(m -> Integer.parseInt((String) m.get("a"))).sum());
            assertEquals(9, data().stream().mapToInt(m -> Integer.parseInt((String) m.get("c"))).sum());
        }
    }
}
