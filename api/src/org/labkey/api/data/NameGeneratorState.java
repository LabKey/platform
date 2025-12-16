package org.labkey.api.data;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.LongHashMap;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpLineage;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpLineageService;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.Tuple3;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.labkey.api.data.NameGenerator.getParentImportAliasFieldKeys;
import static org.labkey.api.exp.api.ExpRunItem.INPUT_PARENT;
import static org.labkey.api.exp.api.ExpRunItem.PARENT_IMPORT_ALIAS_MAP_PROP;
import static org.labkey.api.exp.api.ExperimentService.isAliquotedFromColumn;

public class NameGeneratorState implements AutoCloseable
{
    private final @NotNull NameGenerator _nameGenerator;
    private final boolean _incrementSampleCounts;
    protected final User _user;
    private final Map<String, Object> _batchExpressionContext;
    private Function<Map<String,Long>,Map<String,Long>> getSampleCountsFunction;
    private final Map<String, Integer> _newNames = new CaseInsensitiveHashMap<>();

    protected int _rowNumber = 0;
    private final Map<Tuple3<String, Object, FieldKey>, Object> _lookupCache;
    private final Map<String, ArrayList<Object>> _ancestorCache;
    private final Map<String, ArrayList<Object>> _ancestorSearchCache;
    private final Map<String, Map<String, DbSequence>> _prefixCounterSequences;

    private final NameGenerator.ProjectSampleCounters _sampleCounters;
    private boolean _counterSequencesCleaned = false;
    protected final Container _container;

    protected final Map<Long, ExpMaterial> materialCache = new LongHashMap<>();
    protected final Map<Long, ExpData> dataCache = new LongHashMap<>();
    protected final RemapCache renameCache;
    private final Map<String, Map<String, Object>> objectPropertiesCache = new HashMap<>();
    private final String _nameField;

    public NameGeneratorState(@NotNull NameGenerator nameGenerator, boolean incrementSampleCounts, NameGenerator.SampleNameExpressionSummary expressionSummary)
    {
        this(nameGenerator, incrementSampleCounts, expressionSummary, "Name");
    }

    public NameGeneratorState(@NotNull NameGenerator nameGenerator, boolean incrementSampleCounts, NameGenerator.SampleNameExpressionSummary expressionSummary, String nameField)
    {
        _nameGenerator = nameGenerator;
        _incrementSampleCounts = incrementSampleCounts;
        _container = nameGenerator.getContainer();
        _nameField = nameField;

        DbSequence sampleCounterSequence;
        DbSequence rootCounterSequence;

        if (incrementSampleCounts) // determine if need to incrementRootSampleCount
        {
            DbSequence sampleCountSeq = SampleTypeService.get().getSampleCountSequence(_container, false);
            if ((expressionSummary != null && expressionSummary.hasProjectSampleCounter()) || sampleCountSeq.current() > 0) // if ${sampleCount} is present, or if ${sampleCount} was previously evaluated
            {
                sampleCounterSequence = sampleCountSeq;
                if (sampleCounterSequence != null)
                {
                    long expressionMin = expressionSummary.minProjectSampleCounter() - 1;
                    if (sampleCounterSequence.current() == 0) // initialize existing count when ${sampleCount} is first encountered for a project
                        sampleCounterSequence.ensureMinimum(Math.max(expressionMin, SampleTypeService.get().getProjectSampleCount(_container)));
                    else if (sampleCountSeq.current() < expressionMin)
                        sampleCounterSequence.ensureMinimum(expressionMin);
                }
            }
            else
                sampleCounterSequence = null;

            DbSequence rootCountSeq = SampleTypeService.get().getSampleCountSequence(_container, true);
            if ((expressionSummary != null && expressionSummary.hasProjectSampleRootCounter()) || rootCountSeq.current() > 0) // if ${rootSampleCount} is present, or if ${rootSampleCount} was previously evaluated
            {
                rootCounterSequence = rootCountSeq;
                if (rootCounterSequence != null)
                {
                    long expressionMin = expressionSummary.minProjectSampleRootCounter() - 1;
                    if (rootCountSeq.current() == 0) // initialize existing count when ${rootSampleCount} is first encountered for a project
                        rootCounterSequence.ensureMinimum(Math.max(expressionMin, SampleTypeService.get().getProjectRootSampleCount(_container)));
                    else if (rootCounterSequence.current() < expressionMin)
                        rootCounterSequence.ensureMinimum(expressionMin);
                }
            }
            else
                rootCounterSequence = null;
        }
        else
        {
            sampleCounterSequence = null;
            rootCounterSequence = null;
        }

        _sampleCounters = new NameGenerator.ProjectSampleCounters(sampleCounterSequence, rootCounterSequence);

        // Create the name expression context shared for the entire batch of rows
        Map<String, Object> batchContext = new CaseInsensitiveHashMap<>();
        batchContext.put("BatchRandomId", StringUtilsLabKey.getUniquifier(4));
        batchContext.put("Now", new Date());
        _batchExpressionContext = Collections.unmodifiableMap(batchContext);
        _user = User.getSearchUser();
        _lookupCache = new HashMap<>();
        _ancestorCache = new HashMap<>();
        _ancestorSearchCache = new HashMap<>();
        _prefixCounterSequences = new HashMap<>();
        renameCache = new RemapCache(nameGenerator.isAllBulkRemapCache());
    }

    public boolean isIncrementSampleCounts()
    {
        return _incrementSampleCounts;
    }

    public Map<String, Map<String, DbSequence>> getPrefixCounterSequences()
    {
        return _prefixCounterSequences;
    }

    public void cleanUp()
    {
        if (_counterSequencesCleaned)
            return;

        _sampleCounters.sync();

        for (Map<String, DbSequence> counterSequences : _prefixCounterSequences.values())
        {
            for (DbSequence seq: counterSequences.values())
                if (seq != null)
                    seq.sync();
        }

        _counterSequencesCleaned = true;
    }

    @Override
    public void close()
    {
        _rowNumber = -1;
        cleanUp();
    }

    public String nextName(Map<String, Object> rowMap,
                            Set<ExpData> parentDatas,
                            Set<ExpMaterial> parentSamples,
                            @Nullable List<Supplier<Map<String, Object>>> extraPropsFns,
                            @Nullable NameGenerator altNameGenerator)
            throws NameGenerator.NameGenerationException
    {
        if (_rowNumber == -1)
            throw new IllegalStateException("closed");

        _rowNumber++;
        String name;
        try
        {
            name = genName(rowMap, parentDatas, parentSamples, extraPropsFns, altNameGenerator);
        }
        catch (IllegalArgumentException e)
        {
            throw new NameGenerator.NameGenerationException(_rowNumber, e);
        }

        if (_newNames.containsKey(name))
        {
            throw new NameGenerator.DuplicateNameException(name, _rowNumber, _nameGenerator.getParentTable());
        }
        else
        {
            _newNames.put(name, 1);
        }

        return name;
    }

    private NameGenerator getActiveNameGenerator(@Nullable NameGenerator altNameGenerator)
    {
        return altNameGenerator == null ? _nameGenerator : altNameGenerator;
    }

    private String genName(@NotNull Map<String, Object> rowMap,
                           @Nullable Set<ExpData> parentDatas,
                           @Nullable Set<ExpMaterial> parentSamples,
                           @Nullable List<Supplier<Map<String, Object>>> extraPropsFns,
                           @Nullable NameGenerator altNameGenerator)
            throws IllegalArgumentException
    {
        // If sample counters bound to a column are found, e.g. in the expression "${myDate:dailySampleCount}" the dailySampleCount is bound to myDate column,
        // the sample counters will be incremented for that date when the expression is evaluated -- see SubstitutionFormat.SampleCountSubstitutionFormat.
        // Otherwise, update the sample counters for today's date immediately even if the expression doesn't contain a counter replacement token
        // and put the sample counts into the context so that any sample counters not bound to a column will be replaced; e.g, "${dailySampleCount}".
        // It is important to do this even if a "name" is explicitly provided so the sample counts are accurate.
        Map<String, Long> sampleCounts = null;
        if (_incrementSampleCounts)
        {
            if (!(_nameGenerator.getExpressionSummary() != null && _nameGenerator.getExpressionSummary().hasDateBasedSampleCounter()))
            {
                if (null == getSampleCountsFunction)
                {
                    Date now = (Date)_batchExpressionContext.get("now");
                    getSampleCountsFunction = SampleTypeService.get().getSampleCountsFunction(now);
                }
                sampleCounts = getSampleCountsFunction.apply(null);
            }

            if (_sampleCounters.sampleCounterSequence() != null)
            {
                if (sampleCounts == null)
                    sampleCounts = new HashMap<>();

                sampleCounts.put("sampleCount", _sampleCounters.sampleCounterSequence().next());
            }

            if (_sampleCounters.rootCounterSequence() != null)
            {
                if (sampleCounts == null)
                    sampleCounts = new HashMap<>();

                boolean skipRootSampleCount = altNameGenerator != null; // so far altExpression is not null only when generating aliquots
                if (!skipRootSampleCount)
                    sampleCounts.put("rootSampleCount", _sampleCounters.rootCounterSequence().next());
                else
                    sampleCounts.put("rootSampleCount", _sampleCounters.rootCounterSequence().current());
            }
        }

        // Always execute the extraPropsFns, if available, to increment the ${genId} counter in the non-QueryUpdateService code path.
        // The DataClass and SampleType DataIterators increment the genId value using SimpleTranslator.addSequenceColumn()
        Map<String, Object> extraProps = new HashMap<>();
        if (extraPropsFns != null)
        {
            for (Supplier<Map<String, Object>> fn : extraPropsFns)
            {
                Map<String, Object> props = fn.get();
                if (props != null)
                    extraProps.putAll(props);
            }
        }

        // If a name is already provided, just use it as is
        Object currNameObj = rowMap.get(_nameField);
        if (currNameObj != null)
        {
            String currName = currNameObj.toString();
            if (StringUtils.isNotBlank(currName))
                return currName.trim();
        }

        // allow using alternative expression for evaluation.
        // for example, use AliquotNameExpression instead of NameExpression if sample is aliquot
        NameGenerator activeNameGenerator = getActiveNameGenerator(altNameGenerator);
        if (!activeNameGenerator.getSyntaxErrors().isEmpty())
            throw new IllegalArgumentException("Invalid naming expression. " + StringUtils.join(activeNameGenerator.getSyntaxErrors(), "\n"));

        // Add extra context variables
        Map<FieldKey, Object> ctx = additionalContext(rowMap, parentDatas, parentSamples, sampleCounts, extraProps, altNameGenerator);

        StringExpressionFactory.FieldKeyStringExpression expression = activeNameGenerator.getParsedNameExpression();
        String name;
        if (expression instanceof NameGenerator.NameGenerationExpression nge)
            name = nge.eval(ctx, _prefixCounterSequences);
        else
            name = expression.eval(ctx);
        if (name == null || name.isEmpty())
            throw new IllegalArgumentException("The data provided are not sufficient to create a name using the naming pattern '" + expression.getSource() + "'.  Check the pattern syntax and data values.");

        return name;
    }

    private Object getParentFieldValue(ExpObject parentObject, String fieldStr)
    {
        String field = fieldStr.toLowerCase();

        switch (field)
        {
            case "rowid":
                return parentObject.getRowId();
            case "lsid":
                return parentObject.getLSID();
            case "name":
                return parentObject.getName();
            case "description":
            {
                if (parentObject instanceof ExpMaterial material)
                    return material.getDescription();
                else if (parentObject instanceof ExpData data)
                    return data.getDescription();
            }
            case "created":
                return parentObject.getCreated();
            case "modified":
                return parentObject.getModified();
            case "createdby":
                return parentObject.getCreatedBy();
            case "modifiedby":
                return parentObject.getModifiedBy();
            default:
            {
                if (objectPropertiesCache.containsKey(parentObject.getLSID()))
                    return objectPropertiesCache.get(parentObject.getLSID()).get(field);
                Map<String, Object> properties = new CaseInsensitiveHashMap<>();
                parentObject.getObjectProperties(_user).values().forEach(prop -> {
                    PropertyType pt = null;
                    if (prop.getConceptURI() != null || prop.getRangeURI() != null)
                        pt = PropertyType.getFromURI(prop.getConceptURI(), prop.getRangeURI(), null);
                    if (pt != null)
                    {
                        Object rawObj = prop.getObjectValue();
                        if ("Boolean".equals(pt.getXmlName()) && rawObj instanceof Double)
                        {
                            rawObj = (Double) rawObj < 1.0 ? Boolean.FALSE : Boolean.TRUE;
                        }

                        properties.put(prop.getName(), pt.convert(rawObj));
                    }
                    else
                        properties.put(prop.getName(), prop.getObjectValue());
                });
                objectPropertiesCache.put(parentObject.getLSID(), properties);
                return properties.get(field);
            }
        }
    }

    private void addParentLookupValues(String parentTypeName,
                                       boolean isMaterialParent,
                                       @Nullable Map<String, FieldKey> parentImportAliases,
                                       ExpObject parentObject,
                                       Map<FieldKey, LinkedHashSet<Object>> inputLookupValues,
                                       @Nullable NameGenerator altNameGenerator)
    {
        String inputType = isMaterialParent ? ExpMaterial.MATERIAL_INPUT_PARENT : ExpData.DATA_INPUT_PARENT;
        FieldKey inputFK = FieldKey.fromParts(inputType, parentTypeName);

        Map<FieldKey, List<String>> expParentLookupFields = getExpParentLookupFields(altNameGenerator);
        Set<String> fieldNames = new HashSet<>();
        if (expParentLookupFields.containsKey(inputFK))
            fieldNames.addAll(expParentLookupFields.get(inputFK));
        if (expParentLookupFields.containsKey(FieldKey.fromParts(inputType)))
            fieldNames.addAll(expParentLookupFields.get(FieldKey.fromParts(inputType)));
        if (expParentLookupFields.containsKey(FieldKey.fromParts(INPUT_PARENT)))
            fieldNames.addAll(expParentLookupFields.get(FieldKey.fromParts(INPUT_PARENT)));

        for (String fieldName : fieldNames)
        {
            Object lookupValue = getParentFieldValue(parentObject, fieldName);
            if (lookupValue == null)
                continue;

            // add to Input/<LookupField>
            inputLookupValues.computeIfAbsent(FieldKey.fromParts(INPUT_PARENT, fieldName), (s) -> new LinkedHashSet<>()).add(lookupValue);

            // add to importAlias/<LookupField>
            if (parentImportAliases != null)
            {
                parentImportAliases
                        .entrySet()
                        .stream()
                        .filter(entry -> inputFK.equals(entry.getValue()))
                        .forEach(entry -> inputLookupValues.computeIfAbsent(FieldKey.fromParts(entry.getKey(), fieldName), (s) -> new LinkedHashSet<>()).add(lookupValue));
            }

            // add to <Type>Inputs/<LookupField>
            inputLookupValues.computeIfAbsent(FieldKey.fromParts(inputType, fieldName), (s) -> new LinkedHashSet<>()).add(lookupValue);
            // add to <Type>Inputs/<TypeName>/<LookupField>
            inputLookupValues.computeIfAbsent(FieldKey.fromParts(inputType, parentTypeName, fieldName), (s) -> new LinkedHashSet<>()).add(lookupValue);
        }
    }

    private void addAncestorLookupValues(
            ExpRunItem parentObject,
            Map<FieldKey, LinkedHashSet<Object>> inputLookupValues,
            @Nullable NameGenerator altNameGenerator)
    {
        String parentLsid = parentObject.getLSID();
        Map<FieldKey, NameExpressionAncestorPartOption> partAncestorOptions = getPartAncestorOptions(altNameGenerator);
        for (FieldKey ancestorFieldKey : partAncestorOptions.keySet())
        {
            NameExpressionAncestorPartOption ancestorOptions = partAncestorOptions.get(ancestorFieldKey);
            if (ancestorOptions != null)
            {
                String parentType = ancestorOptions.parentType();
                if (!StringUtils.isEmpty(parentType))
                {
                    if (parentObject instanceof ExpMaterial expMaterial)
                    {
                        if (!expMaterial.getSampleType().getName().equalsIgnoreCase(parentType))
                            continue;
                    }
                    else if (parentObject instanceof ExpData expData)
                    {
                        if (!expData.getDataClass(_user).getName().equalsIgnoreCase(parentType))
                            continue;
                    }
                }
                String ancestorKey = ancestorFieldKey.encode() + "-" + parentObject.getObjectId();

                ArrayList<Object> ancestorLookupValues = new ArrayList<>();

                ExpLineageOptions options = ancestorOptions.options();
                String fieldName = ancestorOptions.lookupColumn();
                Identifiable seed = LsidManager.get().getObject(parentLsid);

                if (ancestorOptions.ancestorSearchType() != null)
                {
                    if (_ancestorSearchCache.containsKey(ancestorKey))
                        ancestorLookupValues = _ancestorSearchCache.get(ancestorKey);
                    else
                    {
                        ExpLineage lineage = ExpLineageService.get().getLineage(_container, _user, seed, options);
                        List<ExpRunItem> candidateAncestors = lineage.findAncestorByType(parentObject, ancestorOptions.ancestorSearchType(), _user);
                        candidateAncestors.sort(Comparator.comparing(Identifiable::getName));
                        for (ExpRunItem candidate : candidateAncestors)
                        {
                            Object lookupValue = getParentFieldValue(candidate, fieldName);
                            if (lookupValue != null)
                                ancestorLookupValues.add(lookupValue);
                        }
                        _ancestorSearchCache.put(ancestorKey, ancestorLookupValues);
                    }
                }
                else
                {
                    if (_ancestorCache.containsKey(ancestorKey))
                        ancestorLookupValues = _ancestorCache.get(ancestorKey);
                    else
                    {
                        ExpLineage lineage = ExpLineageService.get().getLineage(_container, _user, seed, options);
                        List<Pair<ExpLineageOptions.LineageExpType, String>> ancestorPaths = ancestorOptions.ancestorPaths();
                        Set<Identifiable> ancestorObjects = lineage.findAncestorObjects(parentObject, ancestorPaths, _user);

                        for (Identifiable ancestorObject : ancestorObjects)
                        {
                            if (ancestorObject instanceof ExpMaterial || ancestorObject instanceof ExpData)
                            {
                                Object lookupValue = getParentFieldValue((ExpObject) ancestorObject, fieldName);
                                if (lookupValue != null)
                                    ancestorLookupValues.add(lookupValue);
                            }
                        }

                        _ancestorCache.put(ancestorKey, ancestorLookupValues);
                    }
                }

                if (!ancestorLookupValues.isEmpty())
                {
                    inputLookupValues.putIfAbsent(ancestorFieldKey, new LinkedHashSet<>());
                    Set<Object> lookupValues = inputLookupValues.get(ancestorFieldKey);
                    for (Object lookupVal : ancestorLookupValues)
                    {
                        if (!lookupValues.contains(lookupVal))
                            lookupValues.add(lookupVal);
                    }
                }
            }
        }
    }

    private void addParentLookupContext(String parentTypeName/* already decoded */,
                                        String parentName,
                                        boolean isMaterialParent,
                                        @Nullable Map<String, FieldKey> parentImportAliases,
                                        Map<FieldKey, LinkedHashSet<Object>> inputLookupValues,
                                        @Nullable NameGenerator altNameGenerator)
    {
        NameGenerator.ExpressionSummary expressionSummary = getExpressionSummary(altNameGenerator);
        if (!expressionSummary.hasParentLookup() || StringUtils.isEmpty(parentTypeName) || StringUtils.isEmpty(parentName))
            return;

        Map<FieldKey, List<String>> expParentLookupFields = getExpParentLookupFields(altNameGenerator);

        boolean hasTypeLookup = expParentLookupFields.containsKey(FieldKey.fromParts(INPUT_PARENT));

        if (!hasTypeLookup)
        {
            if (isMaterialParent)
            {
                if (expParentLookupFields.containsKey(FieldKey.fromParts(ExpMaterial.MATERIAL_INPUT_PARENT)))
                    hasTypeLookup = true;
                else if (expParentLookupFields.containsKey(FieldKey.fromParts(ExpMaterial.MATERIAL_INPUT_PARENT, parentTypeName)))
                    hasTypeLookup = true;
            }
            else
            {
                if (expParentLookupFields.containsKey(FieldKey.fromParts(ExpData.DATA_INPUT_PARENT)))
                    hasTypeLookup = true;
                else if (expParentLookupFields.containsKey(FieldKey.fromParts(ExpData.DATA_INPUT_PARENT, parentTypeName)))
                    hasTypeLookup = true;
            }
        }

        if (!hasTypeLookup && !expressionSummary.hasAncestorSearch())
            return;

        Map<String, ExpSampleType> sampleTypes = getSampleTypes();
        Map<String, ExpDataClass> dataClasses = getDataClasses();
        ExpObject parentObjectType = isMaterialParent ?
                sampleTypes.computeIfAbsent(parentTypeName, (name) -> SampleTypeService.get().getSampleType(_container, name, true))
                : dataClasses.computeIfAbsent(parentTypeName, (name) -> ExperimentService.get().getDataClass(_container, name, true));
        if (parentObjectType == null)
            throw new RuntimeValidationException("Invalid parent type: " + parentTypeName);

        try
        {
            ExpRunItem parentObject = isMaterialParent ?
                    ExperimentService.get().findExpMaterial(_container, _user, parentName, (ExpSampleType) parentObjectType, renameCache, materialCache)
                    : ExperimentService.get().findExpData(_container, _user, (ExpDataClass) parentObjectType, parentTypeName, parentName, renameCache, dataCache);

            if (parentObject == null)
                throw new RuntimeValidationException("Unable to find parent " + parentName);

            addParentLookupValues(parentTypeName, isMaterialParent, parentImportAliases, parentObject, inputLookupValues, altNameGenerator);

            addAncestorLookupValues(parentObject, inputLookupValues, altNameGenerator);
        }
        catch (ValidationException validationErrors)
        {
            throw new RuntimeValidationException("Unable to find parent " + parentName);
        }
    }

    private Map<FieldKey, Object> additionalContext(@NotNull Map<String, Object> rowMap,
                                                    Set<ExpData> parentDatas,
                                                    Set<ExpMaterial> parentSamples,
                                                    @Nullable Map<String, Long> sampleCounts,
                                                    @Nullable Map<String, Object> extraProps,
                                                    @Nullable NameGenerator altNameGenerator)
    {
        Map<FieldKey, Object> ctx = new HashMap<>(NameGenerator.toFieldKeyMap(_batchExpressionContext));
        ctx.put(FieldKey.fromParts("_rowNumber"), _rowNumber);
        ctx.put(FieldKey.fromParts("RandomId"), StringUtilsLabKey.getUniquifier(4));
        if (sampleCounts != null)
            ctx.putAll(NameGenerator.toFieldKeyMap(sampleCounts));
        if (extraProps != null)
            ctx.putAll(NameGenerator.toFieldKeyMap(extraProps));
        ctx.putAll(NameGenerator.toFieldKeyMap(rowMap));
        if (!ctx.containsKey(FieldKey.fromParts("container")) && _container != null)
            ctx.put(FieldKey.fromParts("container"), _container.getName());

        // TODO: is this still applicable?
        // UploadSamplesHelper uses propertyURIs in the rowMap -- add short column names to the map
        if (getParentTable() != null)
        {
            for (ColumnInfo col : getParentTable().getColumns())
            {
                String propURI = col.getPropertyURI();
                if (rowMap.containsKey(propURI))
                    ctx.put(FieldKey.fromParts(col.getName()), rowMap.get(propURI));
            }
        }

        // If needed, add the parent names to the replacement map
        NameGenerator.ExpressionSummary expressionSummary = getExpressionSummary(altNameGenerator);
        if (expressionSummary.hasParentLookup() || expressionSummary.hasParentInputs())
        {
            Map<FieldKey, Set<String>> inputs = new HashMap<>();
            Map<FieldKey, LinkedHashSet<Object>> inputLookupValues = new HashMap<>();

            inputs.put(FieldKey.fromParts(INPUT_PARENT), new LinkedHashSet<>());
            inputs.put(FieldKey.fromParts(ExpData.DATA_INPUT_PARENT), new LinkedHashSet<>());
            inputs.put(FieldKey.fromParts(ExpMaterial.MATERIAL_INPUT_PARENT), new LinkedHashSet<>());

            Map<String, FieldKey> parentImportAliasFieldKeys = getParentImportAliasFieldKeys((Map<String, String>) ctx.get(FieldKey.fromParts(PARENT_IMPORT_ALIAS_MAP_PROP)));

            if (parentDatas != null)
            {
                if (expressionSummary.hasParentInputs())
                {
                    parentDatas.stream().map(ExpObject::getName).forEachOrdered(parentName -> {
                        inputs.get(FieldKey.fromParts(INPUT_PARENT)).add(parentName);
                        inputs.get(FieldKey.fromParts(ExpData.DATA_INPUT_PARENT)).add(parentName);
                    });
                }

                if (expressionSummary.hasParentLookup())
                {
                    for (ExpData parentObject : parentDatas)
                    {
                        addParentLookupValues(parentObject.getDataClass(_user).getName(), false, parentImportAliasFieldKeys, parentObject, inputLookupValues, altNameGenerator);
                        addAncestorLookupValues(parentObject, inputLookupValues, altNameGenerator);
                    }
                }
            }

            if (parentSamples != null)
            {
                if (expressionSummary.hasParentInputs())
                {
                    parentSamples.stream().map(ExpObject::getName).forEachOrdered(parentName -> {
                        inputs.get(FieldKey.fromParts(INPUT_PARENT)).add(parentName);
                        inputs.get(FieldKey.fromParts(ExpMaterial.MATERIAL_INPUT_PARENT)).add(parentName);
                    });
                }
                if (expressionSummary.hasParentLookup())
                {
                    for (ExpMaterial parentObject : parentSamples)
                    {
                        addParentLookupValues(parentObject.getSampleType().getName(), true, parentImportAliasFieldKeys, parentObject, inputLookupValues, altNameGenerator);
                        addAncestorLookupValues(parentObject, inputLookupValues, altNameGenerator);
                    }
                }
            }

            for (String colName : rowMap.keySet())
            {
                Object value = rowMap.get(colName);
                if (value == null)
                    continue;

                if (expressionSummary.hasParentInputs())
                    addInputs(colName, value, inputs, parentImportAliasFieldKeys);
                if (expressionSummary.hasParentLookup())
                    addParentLookupInput(colName, value, parentImportAliasFieldKeys, inputLookupValues, altNameGenerator);
            }

            // if a single input or lookup is found, return the object, not the list
            Map<FieldKey, Object> inputValues = new HashMap<>();
            inputs.forEach((key, value) -> {
                Object inputValue = value;
                if (value.size() == 1)
                    inputValue = value.iterator().next();
                else if (value.isEmpty())
                    inputValue = null;
                inputValues.put(key, inputValue);
            });
            ctx.putAll(inputValues);

            Map<FieldKey, Object> lookupValues = new HashMap<>();
            inputLookupValues.forEach((key, value) -> lookupValues.put(key, value.size() > 1 ? value : (value.size() == 1 ? value.iterator().next() : null)));
            ctx.putAll(lookupValues);
        }

        // If needed, query to find lookup values
        if (!getExprLookups(altNameGenerator).isEmpty())
        {
            for (Map.Entry<FieldKey, TableInfo> pair : getExprLookups(altNameGenerator).entrySet())
            {
                FieldKey fieldKey = pair.getKey();
                TableInfo lookupTable = pair.getValue();

                FieldKey rootFieldKey = fieldKey.getRootFieldKey();
                String rootName = rootFieldKey.getName();
                Object rootValue = ctx.get(rootFieldKey);
                if (rootValue != null)
                {
                    List<ColumnInfo> pkCols = lookupTable.getPkColumns();
                    if (pkCols.size() != 1)
                        continue;

                    ColumnInfo pkCol = pkCols.get(0);
                    // convert the rootValue to the target pkColumn type
                    if (rootValue instanceof String && !pkCol.isStringType())
                    {
                        try
                        {
                            rootValue = ConvertUtils.convert((String)rootValue, pkCol.getJavaClass());
                        }
                        catch (ConversionException x)
                        {
                            throw new IllegalArgumentException(x);
                        }
                    }

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

                        var select = QueryService.get().getSelectBuilder(lookupTable)
                                .columns(cols.values())
                                .filter(filter);
                        try (Results results = select.select())
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

                    ctx.put(fieldKey, value);
                }
            }
        }

        return ctx;
    }
    
    private void addParentLookupInput(String colName,
                                      Object value,
                                      @Nullable Map<String, FieldKey> parentImportAliases,
                                      Map<FieldKey, LinkedHashSet<Object>> inputLookupValues,
                                      @Nullable NameGenerator altNameGenerator)
    {
        String prefix = null;
        String dataType = null;
        if (isAliquotedFromColumn(colName))
        {
            prefix = ExpMaterial.MATERIAL_INPUT_PARENT;
            dataType = getParentTable() != null ? getParentTable().getName() : null;
        }
        else if (parentImportAliases != null && parentImportAliases.containsKey(colName))
        {
            FieldKey aliasField = parentImportAliases.get(colName);
            prefix = aliasField.getParent().getName();
            dataType = aliasField.getName();
        }
        else
        {
            String[] parts = colName.split("/", 2);
            if (parts.length == 2)
            {
                prefix = parts[0];
                dataType = QueryKey.decodePart(parts[1]);
            }
        }

        if (prefix != null && dataType != null)
        {
            boolean isMaterialParent = prefix.equalsIgnoreCase(ExpMaterial.MATERIAL_INPUT_PARENT);
            boolean isDataParent = prefix.equalsIgnoreCase(ExpData.DATA_INPUT_PARENT);
            if (isMaterialParent || isDataParent)
            {
                for (String parent : parentNames(value, colName))
                    addParentLookupContext(dataType, parent, isMaterialParent, parentImportAliases, inputLookupValues, altNameGenerator);
            }
        }
    }

    private Collection<String> parentNames(Object value, String parentColName)
    {
        return NameGenerator.parentNames(value, parentColName).collect(Collectors.toList());
    }

    private void addInputs(String colName,
                           Object value,
                           Map<FieldKey, Set<String>> inputs,
                           @Nullable Map<String, FieldKey> parentImportAliases)
    {
        String[] parts = colName.split("/", 2);
        String prefix = null;
        String decodedDataType = null;
        if (isAliquotedFromColumn(colName))
        {
            prefix = ExpMaterial.MATERIAL_INPUT_PARENT;
            decodedDataType = getParentTable() != null ? getParentTable().getName() : null;
        }
        else if (parts.length == 1 && parentImportAliases != null && parentImportAliases.containsKey(colName))
        {
            FieldKey aliasField = parentImportAliases.get(colName);
            prefix = aliasField.getParent().getName();
            decodedDataType = aliasField.getName();
        }
        else if (parts.length == 2)
        {
            prefix = parts[0];
            decodedDataType = QueryKey.decodePart(parts[1]);  // data might come in as encoded or decoded
        }

        if (prefix != null && decodedDataType != null)
        {
            String inputsCategory = null;
            if (prefix.equalsIgnoreCase(ExpData.DATA_INPUT_PARENT))
                inputsCategory = ExpData.DATA_INPUT_PARENT;
            else if (prefix.equalsIgnoreCase(ExpMaterial.MATERIAL_INPUT_PARENT))
                inputsCategory = ExpMaterial.MATERIAL_INPUT_PARENT;

            if (inputsCategory != null)
            {
                FieldKey inputField = FieldKey.fromParts(prefix, decodedDataType);
                Collection<String> parents = parentNames(value, colName);
                inputs.get(FieldKey.fromParts(INPUT_PARENT)).addAll(parents);
                inputs.get(FieldKey.fromParts(inputsCategory)).addAll(parents);

                Set<String> dataTypeAltNames = new HashSet<>();
                dataTypeAltNames.add(decodedDataType);
                dataTypeAltNames.add(QueryKey.encodePart(decodedDataType)); // add encoded form in case the original parents column in as encoded but parentValues needs to be updated (for example, strip quotes for comma)
                for (String dataTypeAltName : dataTypeAltNames)
                {
                    inputs.computeIfAbsent(FieldKey.fromParts(INPUT_PARENT, dataTypeAltName),  (s) -> new LinkedHashSet<>()).addAll(parents); // add Inputs/SampleType1
                    if (!parents.isEmpty()) // convert "parent1,parent2" to [parent1, parent2]
                        inputs.computeIfAbsent(FieldKey.fromParts(inputsCategory, dataTypeAltName),  (s) -> new LinkedHashSet<>()).addAll(parents);
                }

                // if import aliases are defined, also add in the inputs under the aliases in case those are used in the name expression
                if (parentImportAliases != null)
                {
                    if (parentImportAliases.containsKey(colName))
                        inputs.computeIfAbsent(inputField,  (s) -> new LinkedHashSet<>()).addAll(parents);
                    Optional<Map.Entry<String, FieldKey>> aliasEntry = parentImportAliases.entrySet().stream().filter(entry -> entry.getValue().equals(inputField)).findFirst();
                    aliasEntry.ifPresent(entry -> inputs.computeIfAbsent(FieldKey.fromParts(entry.getKey()),  (s) -> new LinkedHashSet<>()).addAll(parents));
                }
            }
        }
    }

    public Map<FieldKey, List<String>> getExpParentLookupFields(NameGenerator generator)
    {
        return getActiveNameGenerator(generator).getExpParentLookupFields();
    }
    
    public Map<FieldKey, TableInfo> getExprLookups(NameGenerator generator)
    {
        return getActiveNameGenerator(generator).getExprLookups();
    }
    
    private NameGenerator.ExpressionSummary getExpressionSummary(NameGenerator generator)
    {
        return getActiveNameGenerator(generator).getExpressionSummary();
    }

    private Map<FieldKey, NameExpressionAncestorPartOption> getPartAncestorOptions(NameGenerator generator)
    {
        return getActiveNameGenerator(generator).getPartAncestorOptions();
    }

    private TableInfo getParentTable()
    {
        return _nameGenerator.getParentTable();
    }

    private Map<String, ExpSampleType> getSampleTypes()
    {
        return _nameGenerator.getSampleTypes();
    }

    private Map<String, ExpDataClass> getDataClasses()
    {
        return _nameGenerator.getDataClasses();
    }
    
}
