package org.labkey.experiment.api;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.Results;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.StringExpressionFactory.FieldKeyStringExpression;
import org.labkey.api.util.StringExpressionFactory.AbstractStringExpression.NullValueBehavior;
import org.labkey.api.util.SubstitutionFormat;
import org.labkey.api.util.Tuple3;
import org.labkey.experiment.samples.UploadSamplesHelper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.util.SubstitutionFormat.dailySampleCount;
import static org.labkey.api.util.SubstitutionFormat.monthlySampleCount;
import static org.labkey.api.util.SubstitutionFormat.weeklySampleCount;
import static org.labkey.api.util.SubstitutionFormat.yearlySampleCount;

public class NameGenerator
{
    private final TableInfo _parentTable;
    private final FieldKeyStringExpression _parsedNameExpression;

    // extracted from name expression after parsing
    private boolean _exprHasSampleCounterFormats = false;
    private boolean _exprHasLineageInputs = false;
    private Map<FieldKey, TableInfo> _exprLookups = Collections.emptyMap();

    public NameGenerator(@NotNull String nameExpression, @Nullable TableInfo parentTable, boolean allowSideEffects)
    {
        _parentTable = parentTable;
        _parsedNameExpression = FieldKeyStringExpression.create(nameExpression, false, NullValueBehavior.ReplaceNullWithBlank, allowSideEffects);
        initialize();
    }

    public NameGenerator(@NotNull FieldKeyStringExpression nameExpression, @Nullable TableInfo parentTable)
    {
        _parentTable = parentTable;
        _parsedNameExpression = nameExpression;
        initialize();
    }

    // Inspect the expression looking for:
    //   (a) any sample counter formats bound to a column, e.g. ${column:dailySampleCount}
    //   (b) any lineage input tokens
    //   (c) any replacement tokens that include a lookup, e.g., ${foo/bar}
    private void initialize()
    {
        assert _parsedNameExpression != null;

        boolean hasSampleCounterFormat = false;
        boolean hasLineageInputs = false;
        List<FieldKey> lookups = new ArrayList<>();

        List<StringExpressionFactory.StringPart> parts = _parsedNameExpression.getParsedExpression();
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
                if ("inputs".equalsIgnoreCase(sTok) || UploadSamplesHelper.DATA_INPUT_PARENT.equalsIgnoreCase(sTok) || UploadSamplesHelper.MATERIAL_INPUT_PARENT.equalsIgnoreCase(sTok))
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

    public String generateName(@NotNull Map<String, Object> map)
            throws NameGenerationException
    {
        try (State state = createState(false, false))
        {
            return state.nextName(map, null, null);
        }
    }

    public String generateName(@NotNull Map<String, Object> map,
                             @Nullable Set<ExpData> parentDatas, @Nullable Set<ExpMaterial> parentSamples,
                             boolean incrementSampleCounts)
            throws NameGenerationException
    {
        try (State state = createState(false, incrementSampleCounts))
        {
            return state.nextName(map, parentDatas, parentSamples);
        }
    }

    public void generateNames(@NotNull List<Map<String, Object>> maps,
                              @Nullable Set<ExpData> parentDatas, @Nullable Set<ExpMaterial> parentSamples,
                              boolean skipDuplicates, boolean addUniqueSuffixForDuplicates, boolean incrementSampleCounts)
            throws NameGenerationException
    {
        try (State state = createState(addUniqueSuffixForDuplicates, incrementSampleCounts))
        {
            ListIterator<Map<String, Object>> li = maps.listIterator();
            while (li.hasNext())
            {
                Map<String, Object> map = li.next();
                try
                {
                    String name = state.nextName(map, parentDatas, parentSamples);
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
    }

    /**
     * Create new state object for a batch of names.
     * @param addUniqueSuffixForDuplicates Append a unique suffix for duplicate names, e.g. ".1" or ".2"
     * @param incrementSampleCounts Increment the sample counters for each name generated.
     */
    public State createState(boolean addUniqueSuffixForDuplicates, boolean incrementSampleCounts)
    {
        return new State(addUniqueSuffixForDuplicates, incrementSampleCounts);
    }

    public String generateName(@NotNull State state, @NotNull Map<String, Object> rowMap) throws NameGenerationException
    {
        return state.nextName(rowMap, null, null);
    }


    public class State implements AutoCloseable
    {
        private final boolean _addUniqueSuffixForDuplicates;
        private final boolean _incrementSampleCounts;

        private final Map<String, Object> _batchExpressionContext;
        private final Map<String, Integer> _newNames = new CaseInsensitiveHashMap<>();

        private int _rowNumber = 0;
        private Map<Tuple3<String, Object, FieldKey>, Object> _lookupCache;

        private State(boolean addUniqueSuffixForDuplicates, boolean incrementSampleCounts)
        {
            _addUniqueSuffixForDuplicates = addUniqueSuffixForDuplicates;
            _incrementSampleCounts = incrementSampleCounts;

            // Create the name expression context shared for the entire batch of rows
            Map<String, Object> batchContext = new CaseInsensitiveHashMap<>();
            batchContext.put("BatchRandomId", String.valueOf(new Random().nextInt()).substring(1,5));
            batchContext.put("Now", new Date());
            _batchExpressionContext = Collections.unmodifiableMap(batchContext);

            _lookupCache = new HashMap<>();
        }


        @Override
        public void close()
        {
            _rowNumber = -1;
        }

        private String nextName(Map<String, Object> rowMap, Set<ExpData> parentDatas, Set<ExpMaterial> parentSamples)
                throws NameGenerationException
        {
            if (_rowNumber == -1)
                throw new IllegalStateException("closed");

            _rowNumber++;
            String name;
            try
            {
                name = genName(rowMap, parentDatas, parentSamples);
            }
            catch (IllegalArgumentException e)
            {
                throw new NameGenerationException(_rowNumber, e);
            }

            if (_newNames.containsKey(name))
            {
                if (_addUniqueSuffixForDuplicates)
                {
                    // Add a unique suffix to the end of the name.
                    int count = _newNames.get(name);
                    _newNames.put(name, count + 1);
                    name += "." + count;
                }
                else
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
                               @Nullable Set<ExpMaterial> parentSamples)
                throws IllegalArgumentException
        {
            // If sample counters bound to a column are found, e.g. in the expression "${myDate:dailySampleCount}" the dailySampleCount is bound to myDate column,
            // the sample counters will be incremented for that date when the expression is evaluated -- see SubstitutionFormat.SampleCountSubstitutionFormat.
            // Otherwise, update the sample counters for today's date immediately even if the expression doesn't contain a counter replacement token
            // and put the sample counts into the context so that any sample counters not bound to a column will be replaced; e.g, "${dailySampleCount}".
            // It is important to do this even if a "name" is explicitly provided so the sample counts are accurate.
            Map<String, Integer> sampleCounts = null;
            if (_incrementSampleCounts && !_exprHasSampleCounterFormats)
            {
                Date now = (Date)_batchExpressionContext.get("now");
                sampleCounts = ExperimentServiceImpl.get().incrementSampleCounts(now);
            }

            // If a name is already provided, just use it as is
            String curName = (String)rowMap.get("name");
            if (StringUtils.isNotBlank(curName))
                return curName;

            // Add extra context variables
            Map<String, Object> ctx = additionalContext(rowMap, parentDatas, parentSamples, sampleCounts);

            String name = _parsedNameExpression.eval(ctx);
            if (name == null || name.length() == 0)
                throw new IllegalArgumentException("Can't create new name using the name expression: " + _parsedNameExpression.getSource());

            return name;
        }


        private Map<String, Object> additionalContext(
                @NotNull Map<String, Object> rowMap,
                Set<ExpData> parentDatas,
                Set<ExpMaterial> parentSamples,
                @Nullable Map<String, Integer> sampleCounts)
        {
            Map<String, Object> ctx = new CaseInsensitiveHashMap<>();
            ctx.putAll(_batchExpressionContext);
            ctx.put("_rowNumber", _rowNumber);
            ctx.put("RandomId", String.valueOf(new Random().nextInt()).substring(1,5));
            if (sampleCounts != null)
                ctx.putAll(sampleCounts);
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
                Set<String> allInputs = new LinkedHashSet<>();
                Set<String> dataInputs = new LinkedHashSet<>();
                Set<String> materialInputs = new LinkedHashSet<>();

                if (parentDatas != null)
                {
                    parentDatas.stream().map(ExpObject::getName).forEachOrdered(parentName -> {
                        allInputs.add(parentName);
                        dataInputs.add(parentName);
                    });
                }

                if (parentSamples != null)
                {
                    parentSamples.stream().map(ExpObject::getName).forEachOrdered(parentName -> {
                        allInputs.add(parentName);
                        materialInputs.add(parentName);
                    });
                }

                for (String colName : rowMap.keySet())
                {
                    Object value = rowMap.get(colName);
                    if (value == null)
                        continue;

                    if (colName.startsWith(UploadSamplesHelper.DATA_INPUT_PARENT))
                    {
                        parentNames(value, colName).forEach(parentName -> {
                            allInputs.add(parentName);
                            dataInputs.add(parentName);
                        });
                    }
                    else if (colName.startsWith(UploadSamplesHelper.MATERIAL_INPUT_PARENT))
                    {
                        parentNames(value, colName).forEach(parentName -> {
                            allInputs.add(parentName);
                            materialInputs.add(parentName);
                        });
                    }
                }

                ctx.put("Inputs", allInputs);
                ctx.put("DataInputs", dataInputs);
                ctx.put("MaterialInputs", materialInputs);
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
            return UploadSamplesHelper.parentNames(value, parentColName).collect(Collectors.toList());
        }

    }

    class NameGenerationException extends Exception
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

        int getRowNumber()
        {
            return _rowNumber;
        }
    }

    class DuplicateNameException extends NameGenerationException
    {
        private final String _name;

        DuplicateNameException(String name, int rowNumber)
        {
            super("Duplicate name '" + name + "' on row " + rowNumber, rowNumber);
            _name = name;
        }

        String getName()
        {
            return _name;
        }
    }
}


