/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.StringExpressionFactory.AbstractStringExpression.NullValueBehavior;
import org.labkey.api.util.StringExpressionFactory.FieldKeyStringExpression;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.SubstitutionFormat;
import org.labkey.api.util.Tuple3;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.labkey.api.exp.api.ExpRunItem.INPUT_PARENT;
import static org.labkey.api.exp.api.ExpRunItem.PARENT_IMPORT_ALIAS_MAP_PROP;
import static org.labkey.api.util.SubstitutionFormat.dailySampleCount;
import static org.labkey.api.util.SubstitutionFormat.monthlySampleCount;
import static org.labkey.api.util.SubstitutionFormat.weeklySampleCount;
import static org.labkey.api.util.SubstitutionFormat.yearlySampleCount;

public class NameGenerator
{
    /**
     * full expression: ${NamePrefix:withCounter(counterStartIndex?: number, counterNumberFormat?: string)}
     * use regex to match the content inside the outer ${}
     * Examples:
     *  ${AliquotedFrom}-:withCounter   : parentSample-1
     *  ${AliquotedFrom}-${SourceParent/Property}-:withCounter  : parentSample-parentSource-1
     *  ${AliquotedFrom}.:withCounter() : parentSample.1
     *  ${AliquotedFrom}-:withCounter(1000) : parentSample-1000
     *  ${AliquotedFrom}-:withCounter(1, '000') : parentSample-001
     */
    public static final String WITH_COUNTER_REGEX = "(.+):withCounter\\(?(\\d*)?,?\\s*'?(\\d*)?'?\\)?";
    public static final Pattern WITH_COUNTER_PATTERN = Pattern.compile(WITH_COUNTER_REGEX);

    public static final String COUNTER_SEQ_PREFIX = "NameGenCounter-";

    private final TableInfo _parentTable;

    public FieldKeyStringExpression getParsedNameExpression()
    {
        return _parsedNameExpression;
    }

    private final FieldKeyStringExpression _parsedNameExpression;

    // extracted from name expression after parsing
    private boolean _exprHasSampleCounterFormats = false;
    private boolean _exprHasLineageInputs = false;
    private Map<FieldKey, TableInfo> _exprLookups = Collections.emptyMap();

    public NameGenerator(@NotNull String nameExpression, @Nullable TableInfo parentTable, boolean allowSideEffects, @Nullable Map<String, String> importAliases, @Nullable Container container, Function<String, Long> getNonConflictCountFn, String counterSeqPrefix)
    {
        _parentTable = parentTable;
        _parsedNameExpression = NameGenerationExpression.create(nameExpression, false, NullValueBehavior.ReplaceNullWithBlank, allowSideEffects, container, getNonConflictCountFn, counterSeqPrefix);
        initialize(importAliases);
    }

    public NameGenerator(@NotNull String nameExpression, @Nullable TableInfo parentTable, boolean allowSideEffects, Container container, Function<String, Long> getNonConflictCountFn, String counterSeqPrefix)
    {
        this(nameExpression, parentTable, allowSideEffects, null, container, getNonConflictCountFn, counterSeqPrefix);
    }

    public NameGenerator(@NotNull FieldKeyStringExpression nameExpression, @Nullable TableInfo parentTable)
    {
        _parentTable = parentTable;
        _parsedNameExpression = nameExpression;
        initialize(null);
    }

    public static Stream<String> parentNames(Object value, String parentColName)
    {
        if (value == null)
            return Stream.empty();

        Stream<String> values;
        if (value instanceof String)
        {
            values = Arrays.stream(((String)value).split(","));
        }
        else if (value instanceof Collection)
        {
            Collection<?> coll = (Collection)value;
            values = coll.stream().map(String::valueOf);
        }
        else
        {
            throw new IllegalStateException("Expected string or collection for '" + parentColName + "': " + value);
        }

        return values
                .map(String::trim)
                .filter(s -> !s.isEmpty());
    }

    // Inspect the expression looking for:
    //   (a) any sample counter formats bound to a column, e.g. ${column:dailySampleCount}
    //   (b) any lineage input tokens
    //   (c) any replacement tokens that include a lookup, e.g., ${foo/bar}
    private void initialize(@Nullable Map<String, String> importAliases)
    {
        assert _parsedNameExpression != null;

        boolean hasSampleCounterFormat = false;
        boolean hasLineageInputs = false;
        List<FieldKey> lookups = new ArrayList<>();

        // check for all parts, including those in sub nested expressions
        List<StringExpressionFactory.StringPart> parts = _parsedNameExpression.getDeepParsedExpression();
        for (StringExpressionFactory.StringPart part : parts)
        {
            if (!part.isConstant())
            {
                Object token = part.getToken();
                Collection<SubstitutionFormat> formats = part.getFormats();
                for (SubstitutionFormat format : formats)
                {
                    if (format == dailySampleCount || format == weeklySampleCount || format == monthlySampleCount || format == yearlySampleCount)
                        hasSampleCounterFormat = true;
                }

                String sTok = token.toString().toLowerCase();
                if (INPUT_PARENT.equalsIgnoreCase(sTok)
                        || ExpData.DATA_INPUT_PARENT.equalsIgnoreCase(sTok)
                        || ExpMaterial.MATERIAL_INPUT_PARENT.equalsIgnoreCase(sTok)
                        || (importAliases != null && importAliases.containsKey(sTok)))
                {
                    hasLineageInputs = true;
                }

                if (token instanceof FieldKey)
                {
                    FieldKey fkTok = (FieldKey)token;
                    List<String> fieldParts = fkTok.getParts();

                    // for simple token with no lookups, e.g. ${genId}, don't need to do anything special
                    if (fieldParts.size() == 1)
                        continue;

                    // for now, we only support one level of lookup: ${ingredient/name}
                    // future versions could support multiple levels
                    if (fieldParts.size() > 2)
                        throw new UnsupportedOperationException("Only one level of lookup supported: " + fkTok);

                    if (_parentTable == null)
                        throw new UnsupportedOperationException("Parent table required for name expressions with lookups: " + fkTok);

                    lookups.add(fkTok);
                }
            }
        }

        // for each token with a lookup, get the lookup table and stash it for later
        if (!lookups.isEmpty() && _parentTable != null)
        {
            Map<FieldKey, TableInfo> fieldKeyLookup = new HashMap<>();
            for (FieldKey fieldKey : lookups)
            {
                List<String> fieldParts = fieldKey.getParts();
                assert fieldParts.size() == 2;

                // find column matching the root
                String root = fieldParts.get(0);
                ColumnInfo col = _parentTable.getColumn(root);
                if (col != null)
                {
                    ForeignKey fk = col.getFk();
                    if (fk == null)
                        continue;

                    TableInfo lookupTable = fk.getLookupTableInfo();
                    if (lookupTable == null)
                        continue;

                    List<ColumnInfo> pkCols = lookupTable.getPkColumns();
                    if (pkCols.size() != 1)
                        continue;

                    fieldKeyLookup.put(fieldKey, lookupTable);
                }
            }

            _exprLookups = fieldKeyLookup;
        }

        _exprHasSampleCounterFormats = hasSampleCounterFormat;
        _exprHasLineageInputs = hasLineageInputs;
    }

    public void generateNames(@NotNull State state,
                              @NotNull List<Map<String, Object>> maps,
                              @Nullable Set<ExpData> parentDatas, @Nullable Set<ExpMaterial> parentSamples,
                              @Nullable Supplier<Map<String, Object>> extraPropsFn,
                              boolean skipDuplicates)
            throws NameGenerationException
    {
        ListIterator<Map<String, Object>> li = maps.listIterator();
        while (li.hasNext())
        {
            Map<String, Object> map = li.next();
            try
            {
                String name = state.nextName(map, parentDatas, parentSamples, extraPropsFn, null);
                map.put("name", name);
            }
            catch (DuplicateNameException dup)
            {
                if (skipDuplicates)
                {
                    // Issue 23384: SampleSet: import should ignore duplicate rows when ignore duplicates is selected
                    li.remove();
                }
                else
                    throw dup;
            }
        }
    }

    /**
     * Create new state object for a batch of names.
     * @param incrementSampleCounts Increment the sample counters for each name generated.
     */
    @NotNull
    public State createState(boolean incrementSampleCounts)
    {
        return new State(incrementSampleCounts);
    }

    public String generateName(@NotNull State state, @NotNull Map<String, Object> rowMap) throws NameGenerationException
    {
        return state.nextName(rowMap, null, null, null, null);
    }

    public String generateName(@NotNull State state, @NotNull Map<String, Object> rowMap,
                               @Nullable Set<ExpData> parentDatas, @Nullable Set<ExpMaterial> parentSamples,
                               @Nullable Supplier<Map<String, Object>> extraPropsFn) throws NameGenerationException
    {
        return state.nextName(rowMap, parentDatas, parentSamples, extraPropsFn, null);
    }

    public String generateName(@NotNull State state, @NotNull Map<String, Object> rowMap,
                               @Nullable Set<ExpData> parentDatas, @Nullable Set<ExpMaterial> parentSamples,
                               @Nullable Supplier<Map<String, Object>> extraPropsFn, @Nullable FieldKeyStringExpression altExpression) throws NameGenerationException
    {
        return state.nextName(rowMap, parentDatas, parentSamples, extraPropsFn, altExpression);
    }


    public class State implements AutoCloseable
    {
        private final boolean _incrementSampleCounts;

        private final Map<String, Object> _batchExpressionContext;
        private Function<Map<String,Long>,Map<String,Long>> getSampleCountsFunction;
        private final Map<String, Integer> _newNames = new CaseInsensitiveHashMap<>();

        private int _rowNumber = 0;
        private Map<Tuple3<String, Object, FieldKey>, Object> _lookupCache;

        private State(boolean incrementSampleCounts)
        {
            _incrementSampleCounts = incrementSampleCounts;

            // Create the name expression context shared for the entire batch of rows
            Map<String, Object> batchContext = new CaseInsensitiveHashMap<>();
            batchContext.put("BatchRandomId", StringUtilsLabKey.getUniquifier(4));
            batchContext.put("Now", new Date());
            _batchExpressionContext = Collections.unmodifiableMap(batchContext);

            _lookupCache = new HashMap<>();
        }


        @Override
        public void close()
        {
            _rowNumber = -1;
        }

        private String nextName(Map<String, Object> rowMap, Set<ExpData> parentDatas, Set<ExpMaterial> parentSamples, @Nullable Supplier<Map<String, Object>> extraPropsFn, FieldKeyStringExpression altExpression)
                throws NameGenerationException
        {
            if (_rowNumber == -1)
                throw new IllegalStateException("closed");

            _rowNumber++;
            String name;
            try
            {
                name = genName(rowMap, parentDatas, parentSamples, extraPropsFn, altExpression);
            }
            catch (IllegalArgumentException e)
            {
                throw new NameGenerationException(_rowNumber, e);
            }

            if (_newNames.containsKey(name))
            {
                throw new DuplicateNameException(name, _rowNumber);
            }
            else
            {
                _newNames.put(name, 1);
            }

            return name;
        }

        private String genName(@NotNull Map<String, Object> rowMap,
                               @Nullable Set<ExpData> parentDatas,
                               @Nullable Set<ExpMaterial> parentSamples,
                               @Nullable Supplier<Map<String, Object>> extraPropsFn,
                               @Nullable FieldKeyStringExpression altExpression)
                throws IllegalArgumentException
        {
            // If sample counters bound to a column are found, e.g. in the expression "${myDate:dailySampleCount}" the dailySampleCount is bound to myDate column,
            // the sample counters will be incremented for that date when the expression is evaluated -- see SubstitutionFormat.SampleCountSubstitutionFormat.
            // Otherwise, update the sample counters for today's date immediately even if the expression doesn't contain a counter replacement token
            // and put the sample counts into the context so that any sample counters not bound to a column will be replaced; e.g, "${dailySampleCount}".
            // It is important to do this even if a "name" is explicitly provided so the sample counts are accurate.
            Map<String, Long> sampleCounts = null;
            if (_incrementSampleCounts && !_exprHasSampleCounterFormats)
            {
                if (null == getSampleCountsFunction)
                {
                    Date now = (Date)_batchExpressionContext.get("now");
                    getSampleCountsFunction = SampleTypeService.get().getSampleCountsFunction(now);
                }
                sampleCounts = getSampleCountsFunction.apply(null);
            }

            // Always execute the extraPropsFn, if available, to increment the ${genId} counter in the non-QueryUpdateService code path.
            // The DataClass and SampleType DataIterators increment the genId value using SimpleTranslator.addSequenceColumn()
            Map<String, Object> extraProps = null;
            if (extraPropsFn != null)
            {
                extraProps = extraPropsFn.get();
            }

            // If a name is already provided, just use it as is
            Object currNameObj = rowMap.get("Name");
            if (currNameObj != null)
            {
                String currName = currNameObj.toString();
                if (StringUtils.isNotBlank(currName))
                    return currName.trim();
            }

            // Add extra context variables
            Map<String, Object> ctx = additionalContext(rowMap, parentDatas, parentSamples, sampleCounts, extraProps);

            // allow using alternative expression for evaluation.
            // for example, use AliquotNameExpression instead of NameExpression if sample is aliquot
            String name = (altExpression != null ? altExpression : _parsedNameExpression).eval(ctx);
            if (name == null || name.length() == 0)
                throw new IllegalArgumentException("The data provided are not sufficient to create a name using the naming pattern '" + _parsedNameExpression.getSource() + "'.  Check the pattern syntax and data values.");

            return name;
        }


        private Map<String, Object> additionalContext(
                @NotNull Map<String, Object> rowMap,
                Set<ExpData> parentDatas,
                Set<ExpMaterial> parentSamples,
                @Nullable Map<String, Long> sampleCounts,
                @Nullable Map<String, Object> extraProps)
        {
            Map<String, Object> ctx = new CaseInsensitiveHashMap<>();
            ctx.putAll(_batchExpressionContext);
            ctx.put("_rowNumber", _rowNumber);
            ctx.put("RandomId", StringUtilsLabKey.getUniquifier(4));
            if (sampleCounts != null)
                ctx.putAll(sampleCounts);
            if (extraProps != null)
                ctx.putAll(extraProps);
            ctx.putAll(rowMap);

            // UploadSamplesHelper uses propertyURIs in the rowMap -- add short column names to the map
            if (_parentTable != null)
            {
                for (ColumnInfo col : _parentTable.getColumns())
                {
                   String propURI = col.getPropertyURI();
                   if (rowMap.containsKey(propURI))
                       ctx.put(col.getName(), rowMap.get(propURI));
                }
            }

            // If needed, add the parent names to the replacement map
            if (_exprHasLineageInputs)
            {
                Map<String, Set<String>> inputs = new HashMap<>();
                inputs.put(INPUT_PARENT, new LinkedHashSet<>());
                inputs.put(ExpData.DATA_INPUT_PARENT, new LinkedHashSet<>());
                inputs.put(ExpMaterial.MATERIAL_INPUT_PARENT, new LinkedHashSet<>());

                if (parentDatas != null)
                {
                    parentDatas.stream().map(ExpObject::getName).forEachOrdered(parentName -> {
                        inputs.get(INPUT_PARENT).add(parentName);
                        inputs.get(ExpData.DATA_INPUT_PARENT).add(parentName);
                    });
                }

                if (parentSamples != null)
                {
                    parentSamples.stream().map(ExpObject::getName).forEachOrdered(parentName -> {
                        inputs.get(INPUT_PARENT).add(parentName);
                        inputs.get(ExpMaterial.MATERIAL_INPUT_PARENT).add(parentName);
                    });
                }

                Map<String, String> parentImportAliases = (Map<String, String>) ctx.get(PARENT_IMPORT_ALIAS_MAP_PROP);
                for (String colName : rowMap.keySet())
                {
                    Object value = rowMap.get(colName);
                    if (value == null)
                        continue;

                    String[] parts = colName.split("/", 2);
                    if (parts.length == 2)
                    {
                        addInputs(parts, colName, value, inputs, parentImportAliases);
                    }
                    else if (parentImportAliases != null && parentImportAliases.containsKey(colName))
                    {
                        String colNameForAlias = parentImportAliases.get(colName);
                        parts = colNameForAlias.split("/", 2);
                        addInputs(parts, colNameForAlias, value, inputs, parentImportAliases);
                    }
                }

                ctx.putAll(inputs);
            }

            // If needed, query to find lookup values
            if (!_exprLookups.isEmpty())
            {
                for (Map.Entry<FieldKey, TableInfo> pair : _exprLookups.entrySet())
                {
                    FieldKey fieldKey = pair.getKey();
                    TableInfo lookupTable = pair.getValue();

                    String rootName = fieldKey.getRootName();
                    Object rootValue = ctx.get(rootName);
                    if (rootValue != null)
                    {
                        List<ColumnInfo> pkCols = lookupTable.getPkColumns();
                        if (pkCols.size() != 1)
                            continue;

                        ColumnInfo pkCol = pkCols.get(0);
                        // convert the rootValue to the target pkColumn type
                        if (rootValue instanceof String && !pkCol.isStringType())
                            rootValue = ConvertUtils.convert((String)rootValue, pkCol.getJavaClass());

                        // Cache lookupValues by (rootName, rootValue, fieldKey) -> lookupValue
                        // CONSIDER: Cache key could be (lookupSchema, lookupQuery, lookupColName, value)
                        // TODO: support for multi-valued FKs
                        Tuple3<String, Object, FieldKey> key = Tuple3.of(rootName, rootValue, fieldKey);
                        Object value = _lookupCache.computeIfAbsent(key, (tuple3) -> {
                            Object rootVal = tuple3.second;
                            SimpleFilter filter = new SimpleFilter();
                            filter.addCondition(pkCol, rootVal);

                            FieldKey relativeFieldKey = fieldKey.removeParent(rootName);
                            Collection<FieldKey> fields = Collections.singleton(relativeFieldKey);
                            Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(lookupTable, fields);

                            try (Results results = QueryService.get().select(lookupTable, cols.values(), filter, null))
                            {
                                if (results.next())
                                {
                                    return results.getFieldKeyRowMap().get(relativeFieldKey);
                                }
                            }
                            catch (SQLException e)
                            {
                                throw new RuntimeSQLException(e);
                            }

                            return null;
                        });

                        ctx.put(fieldKey.toString(), value);
                    }
                }
            }

            return ctx;
        }

        private Collection<String> parentNames(Object value, String parentColName)
        {
            return NameGenerator.parentNames(value, parentColName).collect(Collectors.toList());
        }

        private void addInputs(String[] parts, String colName, Object value, Map<String, Set<String>> inputs, Map<String, String> parentImportAliases)
        {
            if (parts.length == 2)
            {
                String inputsCategory = null;
                if (parts[0].equalsIgnoreCase(ExpData.DATA_INPUT_PARENT))
                    inputsCategory = ExpData.DATA_INPUT_PARENT;
                else if (parts[0].equalsIgnoreCase(ExpMaterial.MATERIAL_INPUT_PARENT))
                    inputsCategory = ExpMaterial.MATERIAL_INPUT_PARENT;
                if (inputsCategory != null)
                {
                    Collection<String> parents = parentNames(value, colName);
                    inputs.get(INPUT_PARENT).addAll(parents);
                    inputs.get(inputsCategory).addAll(parents);
                    // if import aliases are defined, also add in the inputs under the aliases in case those are used in the name expression
                    if (parentImportAliases != null)
                    {
                        Optional<Map.Entry<String, String>> aliasEntry = parentImportAliases.entrySet().stream().filter(entry -> entry.getValue().equalsIgnoreCase(colName)).findFirst();
                        aliasEntry.ifPresent(entry -> {
                            inputs.computeIfAbsent(entry.getKey(),  (s) -> new LinkedHashSet<>()).addAll(parents);
                        });
                    }
                }
            }
        }

    }

    /**
     *  Same as FieldKeyExpression, but supports :withCounter syntax
     */
    public static class NameGenerationExpression extends FieldKeyStringExpression
    {
        private final Container _container;
        private Function<String, Long> _getNonConflictCountFn;
        private String _counterSeqPrefix;

        public NameGenerationExpression(String source, boolean urlEncodeSubstitutions, NullValueBehavior nullValueBehavior, boolean allowSideEffects, Container container)
        {
            super(source, urlEncodeSubstitutions, nullValueBehavior, allowSideEffects);
            _container = container;
        }

        public NameGenerationExpression(String source, boolean urlEncodeSubstitutions, NullValueBehavior nullValueBehavior, boolean allowSideEffects, Container container, Function<String, Long> getNonConflictCountFn, String counterSeqPrefix)
        {
            this(source, urlEncodeSubstitutions, nullValueBehavior, allowSideEffects, container);
            _getNonConflictCountFn = getNonConflictCountFn;
            _counterSeqPrefix = counterSeqPrefix;
        }

        public static NameGenerationExpression create(String source, boolean urlEncodeSubstitutions)
        {
            return new NameGenerationExpression(source, urlEncodeSubstitutions, NullValueBehavior.ReplaceNullWithBlank, true, null, null, null);
        }

        public static NameGenerationExpression create(String source, boolean urlEncodeSubstitutions, NullValueBehavior nullValueBehavior, boolean allowSideEffects, Container container, Function<String, Long> getNonConflictCountFn)
        {
            return new NameGenerationExpression(source, urlEncodeSubstitutions, nullValueBehavior, allowSideEffects, container, getNonConflictCountFn, null);
        }
        
        public static NameGenerationExpression create(String source, boolean urlEncodeSubstitutions, NullValueBehavior nullValueBehavior, boolean allowSideEffects, Container container, Function<String, Long> getNonConflictCountFn, String counterSeqPrefix)
        {
            return new NameGenerationExpression(source, urlEncodeSubstitutions, nullValueBehavior, allowSideEffects, container, getNonConflictCountFn, counterSeqPrefix);
        }

        @Override
        protected StringExpressionFactory.StringPart parsePart(String expression)
        {
            Matcher counterMatcher = WITH_COUNTER_PATTERN.matcher(expression);
            if (counterMatcher.find())
            {
                String namePrefixExpression = counterMatcher.group(1);
                int startInd = 0;
                String startIndStr = counterMatcher.group(2);
                String numberFormat = counterMatcher.group(3);
                if (!StringUtils.isEmpty(startIndStr))
                {
                    try
                    {
                        startInd = Integer.valueOf(startIndStr);
                    }
                    catch (NumberFormatException e)
                    {
                        // ignore illegal startInd
                    }
                }

                return new NameGenerator.CounterExpressionPart(namePrefixExpression, startInd, numberFormat, _container, _getNonConflictCountFn, _counterSeqPrefix);
            }

            return super.parsePart(expression);
        }

        @Override
        protected void parse()
        {
            _parsedExpression = new ArrayList<>();
            int start = 0;
            int openIndex;
            int openCount = 0;
            final String openTag = "${";
            final char closeTag = '}';

            while (start < _source.length() && (openIndex = _source.indexOf(openTag, start)) >= 0)
            {
                if (openIndex > 0)
                    _parsedExpression.add(new StringExpressionFactory.ConstantPart(_source.substring(start, openIndex)));

                int subInd = openIndex + 2;
                openCount = 1;

                while (subInd < _source.length())
                {
                    int nextOpen = _source.indexOf(openTag, subInd);
                    int nextClose = _source.indexOf(closeTag, subInd);

                    if (nextOpen == -1 && nextClose == -1)
                        break;

                    if (nextOpen == -1 || nextClose < nextOpen)
                    {
                        openCount--;
                        subInd = nextClose + 1;
                    }
                    else if (nextClose > nextOpen)
                    {
                        openCount++;
                        subInd = nextOpen + 2;
                    }

                    if (openCount == 0)
                        break;

                    if (openCount < 0)
                        throw new IllegalArgumentException("Illegal expression: open and close tags are not matched.");
                }

                if (openCount == 0)
                {
                    String sub = _source.substring(openIndex + 2, subInd - 1);
                    StringExpressionFactory.StringPart part = parsePart(sub);

                    if (part.hasSideEffects() && !_allowSideEffects)
                        throw new IllegalArgumentException("Side-effecting expression part not allowed: " + sub);

                    _parsedExpression.add(part);
                    start = subInd;
                    continue;
                }
                else
                    throw new IllegalArgumentException("Illegal expression: open and close tags are not matched.");

            }

            if (start < _source.length())
                _parsedExpression.add(new StringExpressionFactory.ConstantPart(_source.substring(start)));
        }
    }

    public static class CounterExpressionPart extends StringExpressionFactory.StringPart
    {
        private final String _prefixExpression;
        private final Integer _startIndex;

        private final FieldKeyStringExpression _parsedNameExpression;

        private final Function<String, Long> _getNonConflictCountFn;

        private SubstitutionFormat _counterFormat;

        private final Container _container;

        private final Map<String, DbSequence> _counterSequences = new HashMap<>();

        private final String _counterSeqPrefix;

        public CounterExpressionPart(String expression, int startIndex, String counterFormatStr, Container container, Function<String, Long> getNonConflictCountFn, String counterSeqPrefix)
        {
            _prefixExpression = expression;
            _parsedNameExpression = FieldKeyStringExpression.create(expression, false, StringExpressionFactory.AbstractStringExpression.NullValueBehavior.ReplaceNullWithBlank, true);
            _startIndex = startIndex;
            _container = container;
            _getNonConflictCountFn = getNonConflictCountFn;
            _counterSeqPrefix = StringUtils.isBlank(counterSeqPrefix) ? COUNTER_SEQ_PREFIX : counterSeqPrefix;
            if (!StringUtils.isEmpty(counterFormatStr))
                _counterFormat = new SubstitutionFormat.NumberSubstitutionFormat(counterFormatStr);
        }

        @Override
        public boolean isConstant() { return false; }

        @Override
        public Object getToken()
        {
            return _prefixExpression;
        }

        @NotNull
        @Override
        public Collection<SubstitutionFormat> getFormats()
        {
            return Collections.emptyList();
        }

        @Override
        public String getValue(Map map)
        {
            String prefix = _parsedNameExpression.eval(map);
            if (!_counterSequences.containsKey(prefix))
            {
                long existingCount = -1;

                if (_getNonConflictCountFn != null)
                    existingCount = _getNonConflictCountFn.apply(prefix);

                DbSequence newSequence = DbSequenceManager.getPreallocatingSequence(_container, _counterSeqPrefix + prefix);
                long currentSeqMax = newSequence.current();

                if (existingCount >= currentSeqMax || (_startIndex - 1) > currentSeqMax)
                    newSequence.ensureMinimum(existingCount > (_startIndex - 1) ? existingCount : (_startIndex - 1));

                _counterSequences.put(prefix, newSequence);
            }

            long count = _counterSequences.get(prefix).next();

            Object countStr;
            if (_counterFormat != null)
                countStr = _counterFormat.format(count);
            else
                countStr = String.valueOf(count);

            return prefix + countStr;
        }

        public FieldKeyStringExpression getParsedNameExpression()
        {
            return _parsedNameExpression;
        }

        @Override
        public boolean hasSideEffects()
        {
            return false;
        }

        @Override
        public String toString()
        {
            return "${" + _prefixExpression + "}";
        }
    }

    public class NameGenerationException extends Exception
    {
        private final int _rowNumber;

        NameGenerationException(String message, int rowNumber)
        {
            super(message);
            _rowNumber = rowNumber;
        }

        NameGenerationException(int rowNumber, Throwable t)
        {
            super(t);
            _rowNumber = rowNumber;
        }

        public int getRowNumber()
        {
            return _rowNumber;
        }
    }

    public class DuplicateNameException extends NameGenerationException
    {
        private final String _name;

        DuplicateNameException(String name, int rowNumber)
        {
            super("Duplicate name '" + name + "' on row " + rowNumber, rowNumber);
            _name = name;
        }

        public String getName()
        {
            return _name;
        }
    }

    public static class TestCase extends Assert
    {
        final String aliquotedFrom = "S100";
        final String sourceMeta = "mouse1";

        @Test
        public void testDateFormats()
        {
            Date d = new GregorianCalendar(2011, 11, 3).getTime();
            Map<Object, Object> m = new HashMap<>();
            m.put("d", d);

            {
                StringExpression se = NameGenerationExpression.create("${d:date}", false);
                String s = se.eval(m);
                assertEquals("20111203", s);
            }

            {
                StringExpression se = NameGenerationExpression.create("${d:date('yy-MM-dd')}", false);
                String s = se.eval(m);
                assertEquals("11-12-03", s);
            }

            {
                StringExpression se = NameGenerationExpression.create("${d:date('& yy-MM-dd'):htmlEncode}", false);
                String s = se.eval(m);
                assertEquals("&amp; 11-12-03", s);
            }

            {
                StringExpression se = NameGenerationExpression.create("${d:date('ISO_ORDINAL_DATE')}", false);
                String s = se.eval(m);
                assertEquals("2011-337", s);
            }

            {
                // invalid date format
                StringExpression se = NameGenerationExpression.create("${d:date('yyy-MMMMM-dd')}", false);
                String s = se.eval(m);
                assertEquals("2011-D-03", s);
            }

            {
                // parse a non date value
                StringExpression se = NameGenerationExpression.create("${d:date('yyy-MM-dd')}", false);
                Map<Object, Object> m2 = new HashMap<>();
                m.put("d", "Not a date");
                String s = se.eval(m2);
                assertNull(s);
            }

        }

        @Test
        public void testNumberFormats()
        {
            Double d = 123456.789;
            Map<Object, Object> m = new HashMap<>();
            m.put("d", d);

            {
                StringExpression se = NameGenerationExpression.create("${d:number('0.0000')}", false);
                String s = se.eval(m);
                assertEquals("123456.7890", s);
            }

            {
                StringExpression se = NameGenerationExpression.create("${d:number('000000000')}", false);
                String s = se.eval(m);
                assertEquals("000123457", s);
            }

            {
                // invalid number format
                StringExpression se = NameGenerationExpression.create("${d:number('abcde')}", false);
                String s = se.eval(m);
                assertEquals("abcde123457", s);

            }

            {
                // parse a non number
                try
                {
                    Map<Object, Object> m2 = new HashMap<>();
                    m2.put("d", "not a number");

                    StringExpression se = NameGenerationExpression.create("${d:number('0.00')}", false);
                    se.eval(m2);
                    fail("Expected exception");
                }
                catch (IllegalArgumentException e)
                {
                    // ok
                }

            }
        }

        @Test
        public void testStringFormats()
        {
            Map<Object, Object> m = new HashMap<>();
            m.put("a", "A");
            m.put("b", " B ");
            m.put("empty", "");
            m.put("null", null);
            m.put("list", Arrays.asList("a", "b", "c"));

            {
                StringExpression se = NameGenerationExpression.create(
                        "${null:defaultValue('foo')}|${empty:defaultValue('bar')}|${a:defaultValue('blee')}", false);

                String s = se.eval(m);
                assertEquals("foo|bar|A", s);
            }

            {
                StringExpression se = NameGenerationExpression.create(
                        "${b}|${b:trim}|${empty:trim}|${null:trim}", false);
                String s = se.eval(m);
                assertEquals(" B |B||", s);
            }

            {
                StringExpression se = NameGenerationExpression.create(
                        "${a:prefix('!')}|${a:suffix('?')}|${null:suffix('#')}|${empty:suffix('*')}|${empty:defaultValue('foo'):suffix('@')}", false);
                String s = se.eval(m);
                assertEquals("!A|A?|||foo@", s);
            }

            {
                StringExpression se = NameGenerationExpression.create(
                        "${a:join('-')}|${list:join('-')}|${list:join('_'):prefix('['):suffix(']')}|${empty:join('-')}|${null:join('-')}", false);
                String s = se.eval(m);
                assertEquals("A|a-b-c|[a_b_c]||", s);
            }
        }

        @Test
        public void testCollectionFormats()
        {
            Map<Object, Object> m = new HashMap<>();
            m.put("a", "A");
            m.put("empty", Collections.emptyList());
            m.put("null", null);
            m.put("list", Arrays.asList("a", "b", "c"));

            // CONSIDER: We may want to allow empty string to pass through the collection methods untouched
            try
            {
                StringExpression se = NameGenerationExpression.create("${a:first}", false);

                String s = se.eval(m);
                fail("Expected exception");
            }
            catch (IllegalArgumentException e)
            {
                // ok
            }

            {
                StringExpression se = NameGenerationExpression.create("${null:first}|${empty:first}|${list:first}", false);

                String s = se.eval(m);
                assertEquals("||a", s);
            }

            {
                StringExpression se = NameGenerationExpression.create(
                        "${null:rest}|${empty:rest:join('-')}|${list:rest:join('-')}", false);

                String s = se.eval(m);
                assertEquals("||b-c", s);
            }

            {
                StringExpression se = NameGenerationExpression.create(
                        "${null:last}|${empty:last}|${list:last}", false);

                String s = se.eval(m);
                assertEquals("||c", s);
            }
        }

        private void resetCounter()
        {
            Container c = JunitUtil.getTestContainer();

            DbSequenceManager.delete(c, COUNTER_SEQ_PREFIX + aliquotedFrom + '.');
            DbSequenceManager.delete(c, COUNTER_SEQ_PREFIX + aliquotedFrom + "..");
            DbSequenceManager.delete(c, COUNTER_SEQ_PREFIX + aliquotedFrom + "...");
            DbSequenceManager.delete(c, COUNTER_SEQ_PREFIX + aliquotedFrom + '.' + sourceMeta + ".");
            DbSequenceManager.delete(c, COUNTER_SEQ_PREFIX + aliquotedFrom + ".mouse2.");
            DbSequenceManager.delete(c, COUNTER_SEQ_PREFIX + aliquotedFrom + '-');
            DbSequenceManager.delete(c, COUNTER_SEQ_PREFIX + aliquotedFrom + '-' + sourceMeta + "-");
            DbSequenceManager.delete(c, COUNTER_SEQ_PREFIX + aliquotedFrom + '_');
        }

        @Test
        public void testWithCounter()
        {

            Map<Object, Object> m = new HashMap<>();
            m.put("AliquotedFrom", aliquotedFrom);
            m.put("SourceMeta", sourceMeta);

            Container c = JunitUtil.getTestContainer();
            resetCounter();

            {
                FieldKeyStringExpression se = NameGenerationExpression.create(
                        "${${AliquotedFrom}.:withCounter}", false, NullValueBehavior.ReplaceNullWithBlank, true, c, null);

                ArrayList<StringExpressionFactory.StringPart> parsedExpressions = se.getParsedExpression();

                assertEquals(1, parsedExpressions.size());
                assertEquals(2, se.getDeepParsedExpression().size());
                assertTrue(parsedExpressions.get(0) instanceof NameGenerator.CounterExpressionPart);

                String s = se.eval(m);
                assertEquals("S100.1", s);
            }

            {
                FieldKeyStringExpression se = NameGenerationExpression.create(
                        "${${AliquotedFrom}.${SourceMeta}.:withCounter}", false, NullValueBehavior.ReplaceNullWithBlank, true, c, null);

                ArrayList<StringExpressionFactory.StringPart> parsedExpressions = se.getParsedExpression();

                assertEquals(1, parsedExpressions.size());
                assertEquals(4, se.getDeepParsedExpression().size());
                assertTrue(parsedExpressions.get(0) instanceof NameGenerator.CounterExpressionPart);

                String s = se.eval(m);
                assertEquals("S100.mouse1.1", s);
                s = se.eval(m);
                assertEquals("S100.mouse1.2", s);

                Map<Object, Object> m2 = new HashMap<>();
                m2.put("AliquotedFrom", aliquotedFrom);
                m2.put("SourceMeta", "mouse2");

                s = se.eval(m2);
                assertEquals("S100.mouse2.1", s);
            }

            {
                FieldKeyStringExpression se = NameGenerationExpression.create(
                        "${${AliquotedFrom}-:withCounter}-${SourceMeta}-suffix", false, NullValueBehavior.ReplaceNullWithBlank, true, c, null);

                ArrayList<StringExpressionFactory.StringPart> parsedExpressions = se.getParsedExpression();

                assertEquals(4, parsedExpressions.size());
                assertEquals(5, se.getDeepParsedExpression().size());
                assertTrue(parsedExpressions.get(0) instanceof NameGenerator.CounterExpressionPart);
                assertTrue(parsedExpressions.get(1) instanceof StringExpressionFactory.ConstantPart);
                assertTrue(parsedExpressions.get(2) instanceof StringExpressionFactory.FieldPart);

                String s = se.eval(m);
                assertEquals("S100-1-mouse1-suffix", s);
            }

            {
                FieldKeyStringExpression se = NameGenerationExpression.create(
                        "${${AliquotedFrom}_:withCounter()}", false, NullValueBehavior.ReplaceNullWithBlank, true, c, null);

                ArrayList<StringExpressionFactory.StringPart> parsedExpressions = se.getParsedExpression();

                assertEquals(1, parsedExpressions.size());
                assertEquals(2, se.getDeepParsedExpression().size());
                assertTrue(parsedExpressions.get(0) instanceof NameGenerator.CounterExpressionPart);

                String s = se.eval(m);
                assertEquals("S100_1", s);
                s = se.eval(m);
                assertEquals("S100_2", s);
            }

            {
                FieldKeyStringExpression se = NameGenerationExpression.create(
                        "${${AliquotedFrom}..:withCounter(101)}", false, NullValueBehavior.ReplaceNullWithBlank, true, c, null);

                ArrayList<StringExpressionFactory.StringPart> parsedExpressions = se.getParsedExpression();

                assertEquals(1, parsedExpressions.size());
                assertEquals(2, se.getDeepParsedExpression().size());
                assertTrue(parsedExpressions.get(0) instanceof NameGenerator.CounterExpressionPart);
                NameGenerator.CounterExpressionPart counterPart = (NameGenerator.CounterExpressionPart) parsedExpressions.get(0);
                assertEquals((Integer) 101, counterPart._startIndex);

                String s = se.eval(m);
                assertEquals("S100..101", s);
            }

            {
                FieldKeyStringExpression se = NameGenerationExpression.create(
                        "${${AliquotedFrom}...:withCounter(111, '0000')}", false, NullValueBehavior.ReplaceNullWithBlank, true, c, null);

                ArrayList<StringExpressionFactory.StringPart> parsedExpressions = se.getParsedExpression();

                assertEquals(1, parsedExpressions.size());
                assertEquals(2, se.getDeepParsedExpression().size());
                assertTrue(parsedExpressions.get(0) instanceof NameGenerator.CounterExpressionPart);

                String s = se.eval(m);
                assertEquals("S100...0111", s);
            }

        }

        @After
        public void cleanup()
        {
            resetCounter();
        }

    }

}


