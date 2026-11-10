/*
 * Copyright (c) 2011-2026 LabKey Corporation
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
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderService;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.UnexpectedException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Static helpers for assembling DataIterator pipelines: matching a source iterator's columns to a target
 * TableInfo's columns (by property URI, name, import alias, or JDBC-legal name) and copying or merging the
 * result into that table. Also provides adapters that present an iterator as scrollable, map-based,
 * map-transforming, or a Stream of maps.
 */
public class DataIteratorUtil
{
    public static final String DATA_SOURCE = "dataSource";
    public static final String ETL_DATA_SOURCE = "etl";
    public static final String DUPLICATE_COLUMN_IN_DATA_ERROR = "Two columns mapped to target column '%s'. Check the column names and import aliases for your data.";
    // GH Issue 1585: the reverse of DUPLICATE_COLUMN_IN_DATA_ERROR -- one incoming column that two fields claim.
    public static final String AMBIGUOUS_COLUMN_IN_DESIGN_ERROR = "Column '%s' matches more than one field (%s). Check the field names, labels, and import aliases in the design.";

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

    public static Map<String, Integer> createColumnNameMap(DataIterator di)
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


    /**
     * GH Issue 1585: a token two fields claim at alias or label rank is absent from this map, so a caller resolving
     * through it drops the column instead of picking a winner. Only matchColumns reports one, from TableMap.ambiguous;
     * callers that never reach it -- DefaultQueryUpdateService.updateRow, StudyManager's dataset snapshot mapping --
     * drop it with no error. Insert does not come through here at all: DefaultQueryUpdateService._insert matches each
     * PropertyDescriptor's own name, label and aliases, so an ambiguous label is still consumed there, by every field
     * claiming it. Erroring on those paths needs an error channel they do not have.
     */
    public static Map<String,ColumnInfo> createTableMap(TableInfo target, boolean useImportAliases)
    {
        Map<String,Pair<ColumnInfo,MatchType>> targetAliasesMapWithMatchType = buildTableMap(target, useImportAliases).map;
        Map<String, ColumnInfo> targetAliasesMap = new CaseInsensitiveHashMap<>();

        targetAliasesMapWithMatchType.forEach((tableIdentifier, tablePair) -> targetAliasesMap.put(tableIdentifier, tablePair.first));

        return targetAliasesMap;
    }


    // rank of a match of import column NAME matching various properties of target column
    // MatchType.low is used for matches based on something other than name
    private enum MatchType
    {
        propertyuri,
        name,
        alias,
        jdbcname,
        tsvColumn,
        low
    }

    /**
     * GH Issue 1585: precedence for building the target map, lowest first. Separate from {@link MatchType}, which ranks
     * competing source columns in matchColumns(); each rank reports its old MatchType so that ranking is unchanged.
     */
    private enum TokenRank
    {
        tsvFieldKey(MatchType.tsvColumn),
        userAlias(MatchType.alias),
        label(MatchType.alias),
        propertyUri(MatchType.propertyuri),
        systemAlias(MatchType.alias),
        name(MatchType.name);

        final MatchType matchType;

        TokenRank(MatchType matchType)
        {
            this.matchType = matchType;
        }
    }

    protected static class TableMap
    {
        final Map<String, Pair<ColumnInfo, MatchType>> map;
        /** GH Issue 1585: token -> the fields that tie for it, none of which can be resolved to safely. */
        final Map<String, Set<FieldKey>> ambiguous;

        TableMap(Map<String, Pair<ColumnInfo, MatchType>> map, Map<String, Set<FieldKey>> ambiguous)
        {
            this.map = map;
            this.ambiguous = ambiguous;
        }
    }

    /**
     * GH Issue 1585: whether a tie at this rank is something the designer can fix. A label nobody chose is derived from
     * the field name, so "sample_id" and "sampleId" both carry "Sample Id" -- a naming coincidence with no label to
     * correct, not a design problem. One chosen label is enough to make it one.
     */
    private static boolean isDesignerToken(TokenRank rank, ColumnInfo existing, ColumnInfo incoming)
    {
        if (rank == TokenRank.userAlias)
            return true;
        return rank == TokenRank.label && (ImportAliasable.isChosenLabel(existing) || ImportAliasable.isChosenLabel(incoming));
    }

    private static void putToken(Map<String, Pair<ColumnInfo, TokenRank>> ranked, Map<String, Set<FieldKey>> ambiguous,
            String token, ColumnInfo col, TokenRank rank)
    {
        if (null == token)
            return;

        Pair<ColumnInfo, TokenRank> existing = ranked.get(token);

        if (null == existing || rank.compareTo(existing.second) > 0)
        {
            ranked.put(token, new Pair<>(col, rank));
            // A higher-ranked claim settles a tie recorded earlier, so the token is no longer ambiguous.
            ambiguous.remove(token);
        }
        else if (rank == existing.second && !existing.first.getFieldKey().equals(col.getFieldKey()))
        {
            // Only a tie on a token the designer controls is a design problem worth refusing the import over. A tie on a
            // fieldKey or propertyURI is a framework bug, and erroring on one would break imports that work today.
            if (isDesignerToken(rank, existing.first, col))
            {
                ambiguous.computeIfAbsent(token, k -> new LinkedHashSet<>()).add(existing.first.getFieldKey());
                ambiguous.get(token).add(col.getFieldKey());
            }
            // A tie resolves to the last claimant. That only decides a tie no one reports, since a reported one is
            // dropped from the map below.
            ranked.put(token, new Pair<>(col, rank));
        }
    }


    /**
     * NOTE: matchColumns() handles multiple source columns matching the same target column (usually a data file problem
     * for the user to fix), we don't handle one source column matching multiple target columns (more of an admin design problem).
     * One idea would be to return MultiValuedMap<String,Pair<>>, or check for duplicates entries of the same MatchType.
     */
    protected static TableMap buildTableMap(TableInfo target, boolean useImportAliases)
    {
        return buildTableMap(target, useImportAliases, kindSystemImportAliases(target));
    }

    /** The tokens the target's domain kind claims for its built-in columns, empty when it has no kind. */
    public static Set<String> kindSystemImportAliases(TableInfo target)
    {
        Domain domain = target.getDomain();
        DomainKind<?> kind = null == domain ? null : domain.getDomainKind();
        return null == kind ? Set.of() : kind.getSystemImportAliases();
    }

    protected static TableMap buildTableMap(TableInfo target, boolean useImportAliases, Set<String> kindSystemAliases)
    {
        /* CONSIDER: move this functionality into a TableInfo method so this map (or maps) can be cached */
        List<ColumnInfo> cols = target.getColumns().stream()
                .filter(col -> !col.isMvIndicatorColumn() && !col.isRawValueColumn())
                .toList();

        Map<String, Pair<ColumnInfo, TokenRank>> ranked = new CaseInsensitiveHashMap<>(cols.size()*4);
        Map<String, Set<FieldKey>> ambiguous = new CaseInsensitiveHashMap<>();
        FieldKey containerFieldKey = target.getContainerFieldKey();

        for (ColumnInfo col : cols)
        {
            // Issue 21015: Dataset snapshot over flow assay dataset doesn't pick up stat column values
            // TSVColumnWriter.ColumnHeaderType.queryColumnName format is a FieldKey display value from the column name. Blech.
            // Issue 52777: Use the column's FieldKey - otherwise we convert $$Date to $$ate using fromString() and toDisplayString()
            String tsvQueryColumnName = col.getFieldKey().toDisplayString();
            putToken(ranked, ambiguous, tsvQueryColumnName, col, TokenRank.tsvFieldKey);

            // GH Issue 1585: unconditional -- useImportAliases is about what the designer configured, not about whether
            // the framework's own headers work. For the dataset subject column that is a deliberate widening: an import
            // with the designer's aliases off (DatasetDefinition does this for a managed key) still resolves "ptid".
            for (String alias : col.getSystemImportAliasSet())
                putToken(ranked, ambiguous, alias, col, TokenRank.systemAlias);

            // GH Issue 1585: the container column keeps its user aliases even when the designer's are turned off, because
            // only query metadata can set one there. getContainerFieldKey() insists on a GUID type and prefers a column
            // named "container", so the name test is what still covers a non-GUID Folder column.
            if (useImportAliases || col.getFieldKey().equals(containerFieldKey) || "folder".equalsIgnoreCase(col.getName()))
            {
                for (String alias : col.getImportAliasSet())
                    putToken(ranked, ambiguous, alias, col, TokenRank.userAlias);

                // Be sure we have an alias the column name we generate for TSV exports. See issue 21774
                putToken(ranked, ambiguous, tsvQueryColumnName, col, TokenRank.userAlias);
            }

            putToken(ranked, ambiguous, col.getLabel(), col, TokenRank.label);
            putToken(ranked, ambiguous, col.getPropertyURI(), col, TokenRank.propertyUri);
            putToken(ranked, ambiguous, col.getName(), col, TokenRank.name);
        }

        // Debug, not warn: buildTableMap runs on every coerce step and every cached-map miss, so a single broken design
        // would repeat these lines for the life of the deployment. matchColumns raises the user-visible error.
        ImportAliasable.Helper.removeShadowedTokens(ranked, kindSystemAliases, TokenRank.systemAlias,
                (token, col) -> LOG.debug("Import token '{}' in '{}' is claimed by field '{}' rather than the built-in column it belongs to; leaving it unresolved.",
                        token, target.getName(), col.getName()));

        if (!ambiguous.isEmpty())
            LOG.debug("Ambiguous import columns in '{}': {}", target.getName(), ambiguous);

        // GH Issue 1585: an ambiguous token resolves to nothing at all. A caller that renames the source column to the
        // winner -- the sample type coerce step does, through createTableMap -- would otherwise consume the token before
        // matchColumns ever sees it, and the import would quietly write to whichever field happened to win.
        Map<String, Pair<ColumnInfo, MatchType>> targetAliasesMap = new CaseInsensitiveHashMap<>(ranked.size());
        ranked.forEach((token, pair) -> {
            if (!ambiguous.containsKey(token))
                targetAliasesMap.put(token, new Pair<>(pair.first, pair.second.matchType));
        });

        return new TableMap(targetAliasesMap, ambiguous);
    }


    /* NOTE doesn't check column mapping collisions */
    protected static ArrayList<Pair<ColumnInfo,MatchType>> _matchColumns(DataIterator input, TableInfo target, boolean useImportAliases, Container container)
    {
        return _matchColumns(input, buildTableMap(target, useImportAliases), target, container);
    }

    private static ArrayList<Pair<ColumnInfo,MatchType>> _matchColumns(DataIterator input, TableMap tableMap, TableInfo target, Container container)
    {
        Map<String,Pair<ColumnInfo,MatchType>> targetMap = tableMap.map;
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
                if (!vocabProperties.isEmpty())
                {
                    var propCol = target.getColumn(from.getColumnName());
                    if (null != propCol)
                        to = Pair.of(propCol, MatchType.propertyuri);
                }
            }
            if (null != to && null == to.first)
            {
                LOG.info("Column Info is null here: - {} in {}", from.getColumnName(), target.getName());
            }
            matches.add(to);
        }
        return matches;
    }


    /** throws ValidationException only if there are unresolvable ambiguity in the source->destination column mapping */
    public static ArrayList<ColumnInfo> matchColumns(DataIterator input, TableInfo target, boolean useImportAliases, ValidationException setupError, @Nullable Container container)
    {
        TableMap tableMap = buildTableMap(target, useImportAliases);
        ArrayList<Pair<ColumnInfo,MatchType>> matches = _matchColumns(input, tableMap, target, container);
        MultiValuedMap<FieldKey,Integer> duplicatesMap = new ArrayListValuedHashMap<>(input.getColumnCount()+1);

        for (int i=1 ; i<= input.getColumnCount() ; i++)
        {
            Pair<ColumnInfo,MatchType> match = matches.get(i);
            if (null != match)
                duplicatesMap.put(match.first.getFieldKey(), i);

            // GH Issue 1585: a design where two fields claim one token resolves arbitrarily, so refuse the import rather
            // than write to whichever field happened to win. Only data that actually carries the token is affected, so a
            // domain configured before this check still imports everything else. A column _matchColumns already resolved
            // by propertyURI or vocabulary lookup, or skipped as an MV indicator, never consults the token, so the
            // ambiguity cannot reach it.
            String fromName = input.getColumnInfo(i).getName();
            if (null == match && !fromName.toLowerCase().endsWith(MvColumn.MV_INDICATOR_SUFFIX.toLowerCase()))
            {
                Set<FieldKey> claimants = tableMap.ambiguous.get(fromName);
                if (null != claimants)
                {
                    String fields = claimants.stream().map(FieldKey::getName).collect(Collectors.joining("', '"));
                    setupError.addGlobalError(String.format(AMBIGUOUS_COLUMN_IN_DESIGN_ERROR, fromName, "'" + fields + "'"));
                }
            }
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
                    setupError.addGlobalError(String.format(DUPLICATE_COLUMN_IN_DATA_ERROR, e.getKey().getParent() != null ? e.getKey().toString(): e.getKey().getName()));
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
        try (DataLoader loader = DataLoaderService.get().createLoader(from, null, true, c, TabLoader.TSV_FILE_TYPE))
        {
            loader.setInferTypes(false);
            int count = copy(context, loader, to, c, user);
            if (errors.hasErrors())
                throw errors;
            return count;
        }
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

    /**
     * DataIteratorBuilder.getDataIterator() calls this to simplify implementing the success or close() input contract
     */
    public static @Nullable DataIterator wrapOrClose(DataIteratorBuilder in, DataIteratorContext context, UnaryOperator<DataIterator> wrapper)
    {
        DataIterator di = in.getDataIterator(context);
        if (null == di)
            return null;

        try
        {
            DataIterator out = wrapper.apply(di);
            if (null == out)
                closeQuietly(di);
            return out;
        }
        catch (RuntimeException | Error e)
        {
            closeQuietly(di);
            throw e;
        }
    }

    /*
     * Wrapping functions to add functionality to existing DataIterators
     */

    public static ScrollableDataIterator wrapScrollable(DataIterator di)
    {
        return CachingDataIterator.wrap(di);
    }


    /** @param needMutableMapsReturned whether the maps returned are required to be mutable instances */
    public static MapDataIterator wrapMap(DataIterator in, boolean needMutableMapsReturned)
    {
        if (!needMutableMapsReturned && in instanceof MapDataIterator mapIter && mapIter.supportsGetMap())
        {
            return mapIter;
        }
        return new MapDataIterator.MapDataIteratorImpl(in, needMutableMapsReturned);
    }


    /**
     * This Function<> mechanism is very simple, but less efficient that an operator that takes an input map, and
     * a pre-created ArrayListMap for output.  For efficiency there are other DataIterator base classes to use.
     * Should return ArrayListMap or CaseInsensitiveMap.
     * @param columnEnumerator function to determine the fields that may be part of the post-transform map.
     *                         If null, the columns are assumed to be the same as the columns from the input DataIterator.
     * @param fn the function used to transform the map. May throw a RuntimeValidationException to communicate a problem.
     */
    public static DataIteratorBuilder mapTransformer(DataIteratorBuilder dibIn, Function<List<String>, List<String>> columnEnumerator, Function<Map<String,Object>, Map<String,Object>> fn)
    {
        return context -> new _MapTransformer(dibIn.getDataIterator(context), fn, columnEnumerator);
    }


    public static class _MapTransformer extends MapDataIterator.MapDataIteratorImpl
    {
        ArrayList<ColumnInfo> _columns;
        final Function<Map<String,Object>, Map<String,Object>> _fn;
        Map<String,Object> _sourceMap;
        Map<String,Object> _outputMap;

        _MapTransformer(DataIterator in, Function<Map<String,Object>, Map<String,Object>> fn, @Nullable Function<List<String>, List<String>> columnEnumerator)
        {
            super(wrapMap(in, false), false);
            _fn = fn;

            var map = DataIteratorUtil.createColumnNameMap(in);

            List<String> columnNames = List.copyOf(map.keySet());
            if (columnEnumerator != null)
            {
                columnNames = Collections.unmodifiableList(columnEnumerator.apply(columnNames));
            }

            // use source ColumnInfo where it matches
            _columns = new ArrayList<>(columnNames.size() + 1);
            _columns.add(in.getColumnInfo(0));
            var nameSet = new CaseInsensitiveHashSet();
            for (var name : columnNames)
            {
                if (nameSet.add(name))
                {
                    if (map.containsKey(name))
                        _columns.add(in.getColumnInfo(map.get(name)));
                    else
                        _columns.add(new BaseColumnInfo(name, JdbcType.OTHER));
                }
            }
        }

        @Override
        public int getColumnCount()
        {
            return _columns.size() - 1;
        }

        @Override
        public ColumnInfo getColumnInfo(int i)
        {
            return _columns.get(i);
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            _currentMap = null;
            if (!_input.next())
                return false;
            _sourceMap = ((MapDataIterator)_input).getMap();
            try
            {
                _outputMap = _fn.apply(_sourceMap);
            }
            catch (RuntimeValidationException e)
            {
                throw new BatchValidationException(e.getValidationException());
            }

            return true;
        }

        @Override
        public Map<String, Object> getMap()
        {
            return _outputMap;
        }

        @Override
        public Object get(int i)
        {
            return 0==i ? _input.get(0) : _outputMap.get(_columns.get(i).getName());
        }

        @Override
        public Supplier<Object> getSupplier(int i)
        {
            if (0==i)
                return _input.getSupplier(0);
            final var name = _columns.get(i).getName();
            return () -> _outputMap.get(name);
        }
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


    /** GH Issue 1585: token precedence and collision reporting in {@link #buildTableMap}. */
    public static class TableMapTestCase extends Assert
    {
        private VirtualTable<UserSchema> _table;

        @Before
        public void setUp()
        {
            // A TableInfo needs a DbSchema; no SQL is issued against it
            _table = new VirtualTable<>(CoreSchema.getInstance().getSchema(), "ImportTokenRanks", null);
        }

        private BaseColumnInfo col(String name)
        {
            BaseColumnInfo col = new BaseColumnInfo(name, _table, JdbcType.VARCHAR);
            _table.addColumn(col);
            return col;
        }

        /** getContainerFieldKey() only recognises a GUID-typed column, so the container column has to be one. */
        private BaseColumnInfo guidCol(String name)
        {
            BaseColumnInfo col = new BaseColumnInfo(name, _table, JdbcType.GUID);
            _table.addColumn(col);
            return col;
        }

        /** What PropertyColumn.copyAttributes does to an unlabelled field: the generated label is stored, not derived. */
        private BaseColumnInfo unlabelledCol(String name)
        {
            BaseColumnInfo col = col(name);
            col.setLabel(ColumnInfo.labelFromName(name));
            return col;
        }

        private TableMap build(boolean useImportAliases)
        {
            return buildTableMap(_table, useImportAliases);
        }

        /** Name of the column a token resolves to, or null. */
        private String resolve(TableMap map, String token)
        {
            Pair<ColumnInfo, MatchType> match = map.map.get(token);
            return null == match ? null : match.first.getName();
        }

        @Test
        public void systemAliasBeatsUserAlias()
        {
            col("UserField").setImportAliasesSet(Set.of("container"));
            col("Folder").setSystemImportAliasesSet(Set.of("container"));
            assertEquals("Folder", resolve(build(true), "container"));
        }

        @Test
        public void systemAliasBeatsLabel()
        {
            col("UserField").setLabel("container");
            col("Folder").setSystemImportAliasesSet(Set.of("container"));
            assertEquals("Folder", resolve(build(true), "container"));
        }

        @Test
        public void aSystemAliasOutranksALabelWithUserAliasesOff()
        {
            // The dataset shape: the framework's claim on "ptid" outranks a field labeled "ptid", and holds even with
            // the designer's aliases off.
            col("Labeled").setLabel("ptid");
            col("ParticipantId").setSystemImportAliasesSet(Set.of("ptid", "participantid"));

            TableMap map = build(false);
            assertEquals("ParticipantId", resolve(map, "ptid"));
            assertEquals("ParticipantId", resolve(map, "participantid"));
        }

        @Test
        public void systemAliasAppliesWhenUserAliasesAreOff()
        {
            col("UserField").setImportAliasesSet(Set.of("userToken"));
            col("Folder").setSystemImportAliasesSet(Set.of("systemToken"));

            TableMap map = build(false);
            assertEquals("A framework alias is not subject to useImportAliases", "Folder", resolve(map, "systemToken"));
            assertNull("A user alias is", resolve(map, "userToken"));
        }

        @Test
        public void nameBeatsAnotherFieldsLabel()
        {
            col("Shared");
            col("Other").setLabel("Shared");
            assertEquals("Shared", resolve(build(true), "Shared"));
        }

        @Test
        public void labelBeatsAnotherFieldsUserAlias()
        {
            col("Labeled").setLabel("token");
            col("Aliased").setImportAliasesSet(Set.of("token"));
            assertEquals("Labeled", resolve(build(true), "token"));
        }

        @Test
        public void twoFieldsSharingAnAliasAreAmbiguous()
        {
            col("First").setImportAliasesSet(Set.of("dup"));
            col("Second").setImportAliasesSet(Set.of("dup"));

            TableMap map = build(true);
            assertEquals(Set.of(FieldKey.fromParts("First"), FieldKey.fromParts("Second")), map.ambiguous.get("dup"));
            assertNull("An ambiguous token must not resolve, or a caller that renames the column to the winner "
                    + "consumes it before matchColumns can refuse the import", resolve(map, "dup"));
        }

        @Test
        public void theContainerColumnKeepsItsUserAliasesWhenUserAliasesAreOff()
        {
            // getContainerFieldKey() finds a GUID-typed column named "container" or "folder"; a user's String field
            // named "container" is not one, so it stays subject to useImportAliases.
            guidCol("container").setImportAliasesSet(Set.of("legacyFolder"));
            col("NotTheContainer").setImportAliasesSet(Set.of("userToken"));

            TableMap map = build(false);
            assertEquals("container", resolve(map, "legacyFolder"));
            assertNull("Every other column is still subject to useImportAliases", resolve(map, "userToken"));
        }

        @Test
        public void aNonGuidFolderColumnKeepsTheLegacyExemption()
        {
            // getContainerFieldKey() declines a non-GUID column, so the name test in buildTableMap is the only thing
            // resolving this column's aliases. Dropping it for getContainerFieldKey() alone silently breaks them.
            col("Folder").setImportAliasesSet(Set.of("legacyFolder"));

            assertEquals("Folder", resolve(build(false), "legacyFolder"));
        }

        @Test
        public void twoGeneratedLabelsAreNotAmbiguous()
        {
            // Neither field carries a label the designer chose; both generate "Sample Id", so there is none to correct
            unlabelledCol("sample_id");
            unlabelledCol("sampleId");

            TableMap map = build(true);
            assertFalse("A collision between two generated labels is a field-naming coincidence", map.ambiguous.containsKey("Sample Id"));
            assertEquals("sampleId", resolve(map, "Sample Id"));
        }

        @Test
        public void anExplicitLabelMeetingAGeneratedOneIsAmbiguous()
        {
            unlabelledCol("sample_id");
            col("Other").setLabel("Sample Id");

            TableMap map = build(true);
            assertEquals(Set.of(FieldKey.fromParts("sample_id"), FieldKey.fromParts("Other")), map.ambiguous.get("Sample Id"));
        }

        @Test
        public void twoFieldsSharingALabelAreAmbiguous()
        {
            col("First").setLabel("dup");
            col("Second").setLabel("dup");

            TableMap map = build(true);
            assertEquals(Set.of(FieldKey.fromParts("First"), FieldKey.fromParts("Second")), map.ambiguous.get("dup"));
        }

        @Test
        public void aTieAtAnUnreportedRankResolvesToTheLastClaimant()
        {
            // Two columns sharing a propertyURI is a framework bug, not a design problem, so it is not refused and
            // resolves to the last claimant.
            col("First").setPropertyURI("urn:test#shared");
            col("Second").setPropertyURI("urn:test#shared");

            TableMap map = build(true);
            assertFalse("Only a token the designer controls is reported", map.ambiguous.containsKey("urn:test#shared"));
            assertEquals("Second", resolve(map, "urn:test#shared"));
        }

        @Test
        public void aHigherRankedClaimSettlesAnEarlierTie()
        {
            col("First").setImportAliasesSet(Set.of("dup"));
            col("Second").setImportAliasesSet(Set.of("dup"));
            col("dup");

            TableMap map = build(true);
            assertFalse("A field name outranks both aliases, so nothing is ambiguous", map.ambiguous.containsKey("dup"));
            assertEquals("dup", resolve(map, "dup"));
        }

        @Test
        public void aShadowedKindTokenDoesNotFallThroughToAUserAlias()
        {
            // A legacy field named "folder" replaces the built-in container column, taking the system alias with it
            col("folder");
            col("UserField").setImportAliasesSet(Set.of("Container"));

            TableMap map = buildTableMap(_table, true, Set.of("container"));
            assertNull("A field's own alias must not inherit a token the kind reserves", resolve(map, "Container"));
            assertEquals("folder", resolve(map, "folder"));
        }

        @Test
        public void aKindTokenStillResolvesToTheColumnThatClaimsIt()
        {
            col("Folder").setSystemImportAliasesSet(Set.of("container"));
            col("UserField").setImportAliasesSet(Set.of("Container"));

            assertEquals("Folder", resolve(buildTableMap(_table, true, Set.of("container")), "container"));
        }

        @Test
        public void aColumnNamedForAKindTokenStillWinsIt()
        {
            // Lists and datasets name the column "Container" outright; name rank outranks the reservation
            col("Container");

            assertEquals("Container", resolve(buildTableMap(_table, true, Set.of("container")), "container"));
        }

        @Test
        public void aColumnsOwnRepeatedTokenIsNotAmbiguous()
        {
            // getImportAliasSet is case sensitive, so one column can offer the same token twice at the same rank
            col("Only").setImportAliasesSet(new LinkedHashSet<>(List.of("dup", "DUP")));

            TableMap map = build(true);
            assertFalse("One column claiming a token twice is not a collision", map.ambiguous.containsKey("dup"));
            assertEquals("Only", resolve(map, "dup"));
        }
    }

    public static class TestCase extends Assert
    {
        String csv = "a,b,c\n1,2,3\n4,5,6\n";

        DataIterator data()
        {
            try
            {
                DataLoader dl = new TabLoader.CsvFactory().createLoader(IOUtils.toInputStream(csv, StringUtilsLabKey.DEFAULT_CHARSET), true);
                DataIteratorContext c = new DataIteratorContext();
                return dl.getDataIterator(c);
            }
            catch (IOException x)
            {
                throw UnexpectedException.wrap(x);
            }
        }

        @Test
        public void testStream()
        {
            assertEquals(2, data().stream().count());
            assertEquals(5, data().stream().mapToInt(m -> Integer.parseInt((String) m.get("a"))).sum());
            assertEquals(9, data().stream().mapToInt(m -> Integer.parseInt((String) m.get("c"))).sum());
        }

        @Test
        public void testTransform() throws BatchValidationException
        {
            DataIteratorContext ctx = new DataIteratorContext();
            var tx = mapTransformer((context) -> data(), cols -> List.of("a","b","c","sum"),
                    (row) ->
                    {
                        var ret = new HashMap<>(row);
                        ret.put("a", JdbcType.INTEGER.convert(row.get("a")));
                        ret.put("b", JdbcType.INTEGER.convert(row.get("b")));
                        ret.put("c", JdbcType.INTEGER.convert(row.get("c")));
                        ret.put("sum", (int)ret.get("a") + (int)ret.get("b") + (int)ret.get("c"));
                        return ret;
                    }).getDataIterator(ctx);

            assert tx instanceof MapDataIterator;
            var row = tx.getSupplier(0);
            var a = tx.getSupplier(1);
            var b = tx.getSupplier(2);
            var c = tx.getSupplier(3);
            var sum = tx.getSupplier(4);
            var mdi = (MapDataIterator)tx;
            assertTrue(mdi.supportsGetMap());
            assertTrue(mdi.next());
            assertEquals(1, (int)row.get());
            assertEquals((int)tx.get(4), (int)tx.get(1) + (int)tx.get(2) + (int)tx.get(3));
            assertEquals((int)mdi.getMap().get("sum"), (int)mdi.getMap().get("a") + (int)mdi.getMap().get("b") + (int)mdi.getMap().get("c"));
            assertEquals((int)sum.get(), (int)a.get() + (int)b.get() + (int)c.get());
            assertTrue(mdi.next());
            assertEquals(2, (int)row.get());
            assertEquals((int)tx.get(4), (int)tx.get(1) + (int)tx.get(2) + (int)tx.get(3));
            assertEquals((int)mdi.getMap().get("sum"), (int)mdi.getMap().get("a") + (int)mdi.getMap().get("b") + (int)mdi.getMap().get("c"));
            assertEquals((int)sum.get(), (int)a.get() + (int)b.get() + (int)c.get());
            assertFalse(tx.next());
        }
    }
}
