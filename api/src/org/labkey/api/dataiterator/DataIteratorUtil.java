/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.property.PropertyService;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * User: matthewb
 * Date: 2011-05-31
 * Time: 12:52 PM
 */
public class DataIteratorUtil
{
    public static final String DATA_SOURCE = "dataSource";
    public static final String ETL_DATA_SOURCE = "etl";

    private static final Logger LOG = LogManager.getLogger(DataIteratorUtil.class);

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
        Map<String,Pair<ColumnInfo,MatchType>> targetAliasesMapWithMatchType = _createTableMap(target,useImportAliases);
        Map<String, ColumnInfo> targetAliasesMap = new CaseInsensitiveHashMap<>();

        targetAliasesMapWithMatchType.forEach((tableIdentifier, tablePair) -> targetAliasesMap.put(tableIdentifier, tablePair.first));

        return targetAliasesMap;
    }


    // rank of a match of import column NAME matching various properties of target column
    // MatchType.low is used for matches based on something other than name
    enum MatchType {propertyuri, name, alias, jdbcname, tsvColumn, low}


    /**
     * NOTE: matchColumns() handles multiple source columns matching the same target column (usually a data file problem
     * for the user to fix), we don't handle one source column matching multiple target columns (more of an admin design problem).
     * One idea would be to return MultiValuedMap<String,Pair<>>, or check for duplicates entries of the same MatchType.
     */
    protected static Map<String,Pair<ColumnInfo,MatchType>> _createTableMap(TableInfo target, boolean useImportAliases)
    {
        /* CONSIDER: move this functionality into a TableInfo method so this map (or maps) can be cached */
        List<ColumnInfo> cols = target.getColumns().stream()
                .filter(col -> !col.isMvIndicatorColumn() && !col.isRawValueColumn())
                .collect(Collectors.toList());

        Map<String, Pair<ColumnInfo,MatchType>> targetAliasesMap = new CaseInsensitiveHashMap<>(cols.size()*4);

        // should this be under the useImportAliases flag???
        for (ColumnInfo col : cols)
        {
            // Issue 21015: Dataset snapshot over flow assay dataset doesn't pick up stat column values
            // TSVColumnWriter.ColumnHeaderType.queryColumnName format is a FieldKey display value from the column name. Blech.
            String tsvQueryColumnName = FieldKey.fromString(col.getName()).toDisplayString();
            targetAliasesMap.put(tsvQueryColumnName, new Pair<>(col, MatchType.tsvColumn));
        }

        // should this be under the useImportAliases flag???
        for (ColumnInfo col : cols)
        {
            // Jdbc resultset names have substitutions for special characters. If this is such a column, need the substituted name to match on
            targetAliasesMap.put(col.getJdbcRsName(), new Pair<>(col, MatchType.jdbcname));
        }

        for (ColumnInfo col : cols)
        {
            if (useImportAliases || "folder".equalsIgnoreCase(col.getName()))
            {
                for (String alias : col.getImportAliasSet())
                    targetAliasesMap.put(alias, new Pair<>(col, MatchType.alias));

                // Be sure we have an alias the column name we generate for TSV exports. See issue 21774
                String translatedFieldKey = FieldKey.fromString(col.getName()).toDisplayString();
                targetAliasesMap.put(translatedFieldKey, new Pair<>(col, MatchType.alias));
            }
        }

        for (ColumnInfo col : cols)
        {
            String label = col.getLabel();
            if (null != label)
                targetAliasesMap.put(label, new Pair<>(col, MatchType.alias));
        }

        for (ColumnInfo col : cols)
        {
            String uri = col.getPropertyURI();
            if (null != uri)
            {
                targetAliasesMap.put(uri, new Pair<>(col, MatchType.propertyuri));
                String propName = uri.substring(uri.lastIndexOf('#')+1);
                targetAliasesMap.put(propName, new Pair<>(col, MatchType.alias));
            }
        }

        for (ColumnInfo col : cols)
        {
            String name = col.getName();
            targetAliasesMap.put(name, new Pair<>(col,MatchType.name));
        }

        return targetAliasesMap;
    }


    /* NOTE doesn't check column mapping collisions */
    protected static ArrayList<Pair<ColumnInfo,MatchType>> _matchColumns(DataIterator input, TableInfo target, boolean useImportAliases, Container container)
    {
        Map<String,Pair<ColumnInfo,MatchType>> targetMap = _createTableMap(target, useImportAliases);
        ArrayList<Pair<ColumnInfo,MatchType>> matches = new ArrayList<>(input.getColumnCount()+1);
        matches.add(null);

        for (int i=1 ; i<=input.getColumnCount() ; i++)
        {
            ColumnInfo from = input.getColumnInfo(i);
            if (from.getName().toLowerCase().endsWith(MvColumn.MV_INDICATOR_SUFFIX.toLowerCase()))
            {
                matches.add(null);
                continue;
            }

            Pair<ColumnInfo,MatchType> to = targetMap.get(from.getName());
            if (null == to && null != from.getPropertyURI())
            {
                // Do we actually rely on this anywhere???
                // Like maybe ETL from one study to another where subject name does not match? or assay publish?
                to = targetMap.get(from.getPropertyURI());
                if (null != to)
                    to = new Pair<>(to.first, org.labkey.api.dataiterator.DataIteratorUtil.MatchType.low);
            }
            if (null == to)
            {
                // Check to see if the column i.e. propURI has a property descriptor and vocabulary domain is present
                var vocabProperties = PropertyService.get().findVocabularyProperties(container, Collections.singleton(from.getColumnName()));
                if (vocabProperties.size() > 0)
                {
                    var propCol = target.getColumn(from.getColumnName());
                    if (null != propCol)
                        to = Pair.of(propCol, MatchType.propertyuri);
                }
            }
            if (null != to && null == to.first)
            {
                LOG.info("Column Info is null here: - " +  from.getColumnName() + " in " + target.getName());
            }
            matches.add(to);
        }
        return matches;
    }


    /** throws ValidationException only if there are unresolvable ambiguity in the source->destination column mapping */
    public static ArrayList<ColumnInfo> matchColumns(DataIterator input, TableInfo target, boolean useImportAliases, ValidationException setupError, @Nullable Container container)
    {
        ArrayList<Pair<ColumnInfo,MatchType>> matches = _matchColumns(input, target, useImportAliases, container);
        MultiValuedMap<FieldKey,Integer> duplicatesMap = new ArrayListValuedHashMap<>(input.getColumnCount()+1);

        for (int i=1 ; i<= input.getColumnCount() ; i++)
        {
            Pair<ColumnInfo,MatchType> match = matches.get(i);
            if (null != match)
                duplicatesMap.put(match.first.getFieldKey(), i);
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
                    setupError.addGlobalError("Two columns mapped to target column " + e.getKey().toString() + ". Check the column names and import aliases for your data.");
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


    public static int copy(DataIteratorContext context, DataIterator from, TableInfo to, Container c, User user)
    {
        DataIteratorBuilder builder = new DataIteratorBuilder.Wrapper(from);
        return copy(context, builder, to, c, user);
    }

    // NOTE: first consider if using QueryUpdateService is better
    // this is just a point-to-point copy _without_ triggers
    public static int merge(DataIteratorBuilder from, TableInfo to, Container c, User user) throws BatchValidationException
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
    public static int copy(DataIteratorContext context, DataIteratorBuilder from, TableInfo to, Container c, User user)
    {
        StandardDataIteratorBuilder etl = StandardDataIteratorBuilder.forInsert(to, from, c, user);
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
            LogManager.getLogger(it.getClass()).warn("Unexpected error closing DataIterator", x);
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
