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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.CounterDefinition;
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.EnumTableInfo;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableDescription;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.TestSchema;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.TestContext;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static org.labkey.api.data.ColumnRenderPropertiesImpl.STORAGE_UNIQUE_ID_SEQUENCE_PREFIX;

/**
 * SimpleTranslator starts with no output columns (except row number), you must call add() method to add columns.
 *
 * Note that get(n) may be called more than once for any column.  To simplify the implementation to avoid
 * duplicate errors and make AutoIncrement/Guid easier, we just copy the column data on next().
 *
 * NOTE: this also has the effect that it is possible for columns to 'depend' on columns that precede them
 * in the column list.  This can save some extra nesting of iterators, but it can look confusing so use comments.
 *
 *   e.g. Column [2] can use the result calculated by column [1] by calling SimpleTranslator.this.get(1)
 *
 * see {@link AliasColumn} for an example
 * User: matthewb
 * Date: May 16, 2011
 */
public class SimpleTranslator extends AbstractDataIterator implements DataIterator, ScrollableDataIterator
{
    private static final Logger LOG = LogManager.getLogger(SimpleTranslator.class);

    /**
     * Source column index used for output columns without an missing value indicator
     * or when the missing value column isn't present in the source DataIterator.
     */
    static final int NO_MV_INDEX = 0;

    private DataIterator _data;
    protected Object[] _row = null;
    private Container _mvContainer;
    private Map<String,String> _missingValues = Collections.emptyMap();
    private Map<String,Integer> _inputNameMap = null;

    protected final ArrayList<Pair<ColumnInfo, Supplier>> _outputColumns = new ArrayList<>()
    {
        @Override
        public boolean add(Pair<ColumnInfo, Supplier> columnInfoCallablePair)
        {
            assert null == _row;
            return super.add(columnInfoCallablePair);
        }
    };

    public SimpleTranslator(DataIterator source, DataIteratorContext context)
    {
        super(context);
        _data = source;
        _outputColumns.add(new Pair<>(new BaseColumnInfo(source.getColumnInfo(0)), new PassthroughColumn(0)));
    }

    protected DataIterator getInput()
    {
        return _data;
    }

    public void setInput(DataIterator it)
    {
        _data = it;
    }

    public void setMvContainer(Container c)
    {
        _mvContainer = c;
        if (null == c)
            _missingValues = new HashMap<>();
        else
            _missingValues = MvUtil.getIndicatorsAndLabels(c);
    }

    protected boolean validMissingValue(String mv)
    {
        return _missingValues.containsKey(mv);
    }

    protected Object addConversionException(String fieldName, @Nullable Object value, @Nullable JdbcType target, Exception x)
    {
        String msg;
        if (null != value && null != target)
        {
            msg = ConvertHelper.getStandardConversionErrorMessage(value, fieldName, target.getJavaClass());
        }
        else if (null != x)
            msg = StringUtils.defaultString(x.getMessage(), x.toString());
        else
            msg = "Could not convert value";
        addFieldError(fieldName, msg);
        return null;
    }

    public static class RemapPostConvert
    {
        private final TableInfo _targetTable;
        private final boolean _includeTitleColumn;
        private final RemapMissingBehavior _missing;
        private boolean _includePkLookup;               // if true, will perform an initial PK lookup before attempting the AK lookup

        private final boolean _allowBulkLoads;
        private final Set<Pair<ColumnInfo, ColumnInfo>> _bulkLoads = new HashSet<>();

        private List<Triple<ColumnInfo, ColumnInfo, MultiValuedMap<?, ?>>> _maps = null;
        private Triple<ColumnInfo, ColumnInfo, MultiValuedMap<?, ?>> _titleColumnLookupMap = null;
        private Pair<ColumnInfo, Map<?, ?>> _pkColumnLookupMap = null;

        public RemapPostConvert(@NotNull TableInfo targetTable, boolean includeTitleColumn, RemapMissingBehavior missing, boolean allowBulkLoads)
        {
            this(targetTable, includeTitleColumn, missing, allowBulkLoads, false);
        }

        public RemapPostConvert(@NotNull TableInfo targetTable, boolean includeTitleColumn, RemapMissingBehavior missing, boolean allowBulkLoads, boolean includePkLookup)
        {
            _targetTable = targetTable;
            _includeTitleColumn = includeTitleColumn;
            _missing = missing;
            _allowBulkLoads = allowBulkLoads;
            _includePkLookup = includePkLookup;
        }

        public void setIncludePkLookup(boolean includePkLookup)
        {
            _includePkLookup = includePkLookup;
        }

        public ColumnInfo getPkColumn()
        {
            return _targetTable.getPkColumns().get(0);
        }

        private List<Triple<ColumnInfo, ColumnInfo, MultiValuedMap<?, ?>>> getMaps()
        {
            if (_maps == null)
            {
                _maps = new ArrayList<>();

                ColumnInfo pkCol = getPkColumn();
                Set<ColumnInfo> seen = new HashSet<>();

                // See similar check in AbstractForeignKey.allowImportByAlternateKey()
                // The lookup table must meet the following requirements:
                // - Has a single primary key
                // - Has a unique index over a single column that isn't the primary key
                // - The column in the unique index must be a string type
                for (Pair<TableInfo.IndexType, List<ColumnInfo>> index : _targetTable.getUniqueIndices().values())
                {
                    if (index.getKey() != TableInfo.IndexType.Unique)
                        continue;

                    if (index.getValue().size() != 1)
                        continue;

                    ColumnInfo col = index.getValue().get(0);
                    if (!seen.add(col))
                        continue;

                    if (pkCol == col)
                        continue;

                    if (!col.getJdbcType().isText())
                        continue;

                    _maps.add(Triple.of(pkCol, col, new ArrayListValuedHashMap()));
                }

                if (_includeTitleColumn)
                {
                    ColumnInfo titleColumn = _targetTable.getTitleColumn() != null ? _targetTable.getColumn(_targetTable.getTitleColumn()) : null;
                    if (titleColumn != null && !seen.contains(titleColumn))
                    {
                        _titleColumnLookupMap = Triple.of(pkCol, titleColumn, new ArrayListValuedHashMap());
                    }
                }

                if (_includePkLookup)
                {
                    _pkColumnLookupMap = Pair.of(pkCol, new HashMap<>());
                }
            }
            return _maps;
        }

        public Object mappedValue(Object k)
        {
            if (null == k)
                return null;

            // Don't attempt to convert List or Map values
            if (k instanceof Map || k instanceof List)
                return k;

            List<Triple<ColumnInfo, ColumnInfo, MultiValuedMap<?,?>>> maps = getMaps();

            if (_pkColumnLookupMap != null)
            {
                Object v = fetch(_pkColumnLookupMap, k);
                if (v != null)
                    return v;
            }

            for (Triple<ColumnInfo, ColumnInfo, MultiValuedMap<?,?>> triple : maps)
            {
                Object v = fetch(triple, k);
                if (v != null)
                    return v;
            }

            if (_titleColumnLookupMap != null)
            {
                Object v = fetch(_titleColumnLookupMap, String.valueOf(k));
                if (v != null)
                    return v;
            }

            switch (_missing)
            {
                case Null:          return null;
                case OriginalValue: return k;
                case Error:
                default:            throw new ConversionException("Could not translate value: " + String.valueOf(k));
            }
        }

        private final Object MISS = new Object();

        // While there should be at most one matching value for lookup targets with a true unique constraint,
        // using a multi-valued map allows us to also work with things that are almost always unique, like
        // exp.Material names, when only a single value matches
        private Object fetch(Triple<ColumnInfo, ColumnInfo, MultiValuedMap<?,?>> triple, Object k)
        {
            final ColumnInfo pkCol = triple.getLeft();
            final ColumnInfo altKeyCol = triple.getMiddle();
            final MultiValuedMap map = triple.getRight();

            // check if we've already fetched the key
            Collection<Object> vs;
            if (map.containsKey(k))
            {
                vs = map.get(k);
                assert vs != null && !vs.isEmpty() : "map should contain values or the MISS marker";
            }
            else
            {
                Collection<Object> bulkLoaded = null;
                if (_allowBulkLoads && _bulkLoads.add(Pair.of(pkCol, altKeyCol)))
                {
                    TableSelector bulkSelector = createSelector(pkCol, altKeyCol);
                    bulkSelector.fillMultiValuedMap(map);
                    bulkLoaded = map.get(k);
                }

                if (bulkLoaded == null)
                {
                    TableSelector ts = createSelector(pkCol, altKeyCol, k);
                    ts.fillMultiValuedMap(map);
                    vs = map.get(k);
                }
                else
                {
                    vs = bulkLoaded;
                }

                // ArrayListValuedHashMap returns an empty collection if 'k' is not in the map.
                // If there are no values in the database, stash a MISS marker to avoid re-fetching.
                assert vs != null;
                if (vs.isEmpty())
                    map.put(k, MISS);
            }

            Object v = getSingleValue(k, vs);
            if (v == MISS)
                return null;

            return v;
        }

        // currently only used by the PK maps
        private Object fetch(Pair<ColumnInfo, Map<?,?>> pair, Object k)
        {
            final ColumnInfo pkCol = pair.getKey();
            Map map = pair.getValue();

            if (map.containsKey(k))
            {
                Object v = map.get(k);
                if (v == MISS)
                    return null;
                return v;
            }
            else
            {
                if (_allowBulkLoads && _bulkLoads.add(Pair.of(pkCol, pkCol)))
                {
                    TableSelector ts = createSelector(pkCol, pkCol);
                    ts.forEach(pkCol.getJavaObjectClass(), (Object pk) -> map.put(pk, pk));
                }
                else
                {
                    TableSelector ts = createSelector(pkCol, pkCol, k);
                    ts.forEach(pkCol.getJavaObjectClass(), (Object pk) -> map.put(pk, pk));
                }

                if (map.containsKey(k))
                    return map.get(k);
                else
                {
                    map.put(k, MISS);
                    return null;
                }
            }
        }

        private TableSelector createSelector(ColumnInfo pkCol, ColumnInfo altKeyCol)
        {
            return createSelector(pkCol, altKeyCol, new SimpleFilter());
        }

        private TableSelector createSelector(ColumnInfo pkCol, ColumnInfo altKeyCol, Object value)
        {
            return createSelector(pkCol, altKeyCol, new SimpleFilter(altKeyCol.getFieldKey(), value));
        }

        private TableSelector createSelector(ColumnInfo pkCol, ColumnInfo altKeyCol, SimpleFilter filter)
        {
            return new TableSelector(_targetTable, Arrays.asList(altKeyCol, pkCol), filter, null).setMaxRows(1_000_000);
        }


        private Object getSingleValue(Object k, Collection<?> vs)
        {
            if (vs.size() == 1)
                return vs.iterator().next();

            throw new ConversionException("Found " + vs.size() + " values matching: " + String.valueOf(k));
        }
    }


    protected class PassthroughColumn implements Supplier
    {
        final int index;

        PassthroughColumn(int index)
        {
            this.index = index;
        }

        @Override
        public Object get()
        {
            return _data.get(index);
        }
    }

    // For fields that use "Derivation Data Scope", such as sample type property fields,
    // which can be either aliquot-specific or sample metadata, use a custom get to discard/retain field values based on field property.
    protected class DerivationScopedConvertColumn extends SimpleConvertColumn
    {
        final int derivationDataColInd;
        final int index;
        final boolean isDerivation;
        final String presentDerivationWarning;
        final String presentNonDerivationWarning;

        final SimpleConvertColumn _convertCol;

        public DerivationScopedConvertColumn(int index, SimpleConvertColumn convertCol, int derivationDataColInd, boolean isDerivation, @Nullable String presentDerivationWarning, @Nullable String presentNonDerivationWarning)
        {
            super(convertCol.fieldName, convertCol.index, convertCol.type);
            _convertCol = convertCol;
            this.index = index;
            this.derivationDataColInd = derivationDataColInd;
            this.isDerivation = isDerivation;
            this.presentDerivationWarning = presentDerivationWarning;
            this.presentNonDerivationWarning = presentNonDerivationWarning;
        }

        @Override
        protected Object convert(Object o)
        {
            Object thisValue =  _convertCol.convert(o);

            return getDerivationData(thisValue, derivationDataColInd, isDerivation, presentDerivationWarning, presentNonDerivationWarning);

        }
    }

    // For fields that use "Derivation Data Scope", such as sample type property fields,
    // which can be either aliquot-specific or sample metadata, use a custom get to discard/retain field values based on field property.
    protected class DerivationScopedColumn implements Supplier
    {
        final int derivationDataColInd;
        final int index;
        final boolean isDerivation;

        final String presentDerivationWarning;
        final String presentNonDerivationWarning;

        public DerivationScopedColumn(int index, int derivationDataColInd, boolean isDerivation, @Nullable String presentDerivationWarning, @Nullable String presentNonDerivationWarning)
        {
            this.index = index;
            this.derivationDataColInd = derivationDataColInd;
            this.isDerivation = isDerivation;
            this.presentDerivationWarning = presentDerivationWarning;
            this.presentNonDerivationWarning = presentNonDerivationWarning;
        }

        @Override
        public Object get()
        {
            Object thisValue =  _data.get(index);
            return getDerivationData(thisValue, derivationDataColInd, isDerivation, presentDerivationWarning, presentNonDerivationWarning);
        }
    }

    /**
     * @param thisValue the original field value
     * @param derivationDataColInd the col index for the field used to determine if a record is child or parent
     * @param isDerivationField if this field is a child only field
     * @param presentDerivationWarning the warning msg to log if a child field is present for a parent record
     * @param presentNonDerivationWarning the warning msg to log if a parent field is present for a child record
     * @return
     */
    private Object getDerivationData(Object thisValue, int derivationDataColInd, boolean isDerivationField, @Nullable String presentDerivationWarning, @Nullable String presentNonDerivationWarning)
    {
        Object derivationData = derivationDataColInd < 0 ? null : _data.get(derivationDataColInd);
        if ((isDerivationField && derivationData != null)
                || (!isDerivationField && derivationData == null))
            return thisValue;

        if (thisValue != null)
        {
            if (isDerivationField && presentDerivationWarning != null)
                LOG.warn(presentDerivationWarning);
            else if (!isDerivationField && presentNonDerivationWarning != null)
                LOG.warn(presentNonDerivationWarning);
        }

        return null;
    }


    protected class AliasColumn extends SimpleConvertColumn
    {
        AliasColumn(String fieldName, int index)
        {
            super(fieldName, index, null);
        }

        AliasColumn(String fieldName, int index, JdbcType convert)
        {
            super(fieldName, index, convert);
        }

        @Override
        protected Object getSourceValue()
        {
            return SimpleTranslator.this.get(index);
        }
    }


    /** coalease, return the first column if non-null, else the second column */
    protected class CoalesceColumn implements Supplier
    {
        final Supplier _first;
        final Supplier _second;

        CoalesceColumn(int first, Supplier second)
        {
            _first = new PassthroughColumn(first);
            _second = second;
        }

        CoalesceColumn(Supplier first, Supplier second)
        {
            _first = first;
            _second = second;
        }

        @Override
        public Object get()
        {
            Object v = _first.get();
            if (v instanceof String)
                v = StringUtils.isEmpty((String)v) ? null : v;
            if (null != v)
                return v;
            return _second.get();
        }
    }



    protected class SimpleConvertColumn implements Supplier<Object>
    {
        final int index;
        final @Nullable JdbcType type;
        final String fieldName;
        final boolean _preserveEmptyString;

        SimpleConvertColumn(String fieldName, int indexFrom, @Nullable JdbcType to)
        {
            this.fieldName = fieldName;
            this.index = indexFrom;
            this.type = to;
            _preserveEmptyString = false;
        }

        public SimpleConvertColumn(String fieldName, int indexFrom, @Nullable JdbcType to, boolean preserveEmptyString)
        {
            this.index = indexFrom;
            this.type = to;
            this.fieldName = fieldName;
            _preserveEmptyString = preserveEmptyString;
        }

        @Override
        final public Object get()
        {
            Object value = getSourceValue();
            try
            {
                return convert(value);
            }
            catch (ConversionException x)
            {
                return addConversionException(fieldName, value, type, x);
            }
        }

        protected Object convert(Object o)
        {
            if (o instanceof String && JdbcType.VARCHAR.equals(type) && "".equals(o) && _preserveEmptyString)
                return "";
            return null==type ? o : type.convert(o);
        }

        protected Object getSourceValue()
        {
            return _data.get(index);
        }
    }


    public static class GuidColumn implements Supplier
    {
        @Override
        public Object get()
        {
            return GUID.makeGUID();
        }
    }


    public class ContainerColumn implements Supplier
    {
        final UserSchema us;
        final TableInfo tableInfo;
        final String containerId;
        final int idx;

        final Set<Object> allowableContainers = new HashSet<>();

        public ContainerColumn(UserSchema us, TableInfo tableInfo, String containerId, int idx)
        {
            this.us = us;
            this.tableInfo = tableInfo;
            this.containerId = containerId;
            this.idx = idx;
        }

        @Override
        public Object get()
        {
            // Related to: Issues 15301 and 32961: allow workbooks records to be deleted/updated from the parent container
            Object rowContainerVal = idx > 0 ? _data.get(idx) : null;
            if (rowContainerVal != null && us != null)
            {
                if (rowContainerVal instanceof Container)
                {
                    rowContainerVal = ((Container)rowContainerVal).getId();
                }

                if (allowableContainers.contains(rowContainerVal))
                {
                    return rowContainerVal;
                }

                Container rowContainer = UserSchema.translateRowSuppliedContainer(rowContainerVal, us.getContainer(), us.getUser(), tableInfo, UpdatePermission.class, getDataSource());
                if (rowContainer != null)
                {
                    if (!this.us.getContainer().allowRowMutationForContainer(rowContainer))
                    {
                        getRowError().addError(new SimpleValidationError("Row supplied container value: " + rowContainerVal + " cannot be used for actions against the container: " + us.getContainer().getPath()));
                        LOG.warn("Resolved container to " + rowContainer.getPath() + " but rejected as valid location for import into " + us.getContainer().getPath() + " in " + us.getSchemaName() + "." + tableInfo.getPublicSchemaName());
                    }
                    else
                    {
                        allowableContainers.add(rowContainerVal);
                    }

                    return rowContainer.getId();
                }
                else
                {
                    // only log if the incoming value is GUID-like
                    if (rowContainerVal instanceof String && GUID.isGUID((String)rowContainerVal))
                    {
                        LOG.warn("Failed to resolve container value '" + rowContainerVal + "' to container for import into " + us.getSchemaName() + "." + tableInfo.getPublicSchemaName() + ", defaulting to original target container of " + us.getContainer().getPath());
                    }
                }
            }

            return containerId;
        }
    }

    public static class ConstantColumn implements Supplier
    {
        final Object k;

        public ConstantColumn(Object k)
        {
            this.k = k;
        }

        @Override
        public Object get()
        {
            return k;
    }
    }


    public static class AutoIncrementColumn implements Supplier
    {
        private int _autoIncrement = -1;

        protected int getFirstValue()
        {
            return 1;
        }

        @Override
        public Object get()
        {
            if (_autoIncrement == -1)
                _autoIncrement = getFirstValue();
            return _autoIncrement++;
        }
    }


    private class MissingValueConvertColumn extends SimpleConvertColumn
    {
        boolean supportsMissingValue = true;
        int indicator;

        MissingValueConvertColumn(String fieldName, int index,JdbcType to)
        {
            super(fieldName, index, to);
            indicator = NO_MV_INDEX;
        }

        MissingValueConvertColumn(String fieldName, int index, int indexIndicator, @Nullable JdbcType to)
        {
            super(fieldName, index, to);
            indicator = indexIndicator;
        }


        @Override
        public Object convert(Object value)
        {
            if (value instanceof MvFieldWrapper)
                return value;

            Object mv = NO_MV_INDEX ==indicator ? null : _data.get(indicator);

            if (value instanceof String && StringUtils.isEmpty((String)value))
                value = null;
            if (null != mv && !(mv instanceof String))
                mv = String.valueOf(mv);
            if (StringUtils.isEmpty((String)mv))
                mv = null;

            if (supportsMissingValue && null == mv && null != value)
            {
                String s = value.toString();
                if (validMissingValue(s))
                {
                    mv = s;
                    value = null;
                }
            }

            if (null != value)
                value = innerConvert(value);
            
            if (supportsMissingValue && null != mv)
            {
                if (!validMissingValue((String)mv))
                {
                    getRowError().addFieldError(_data.getColumnInfo(index).getName(),"Value is not a valid missing value indicator: " + mv.toString());
                    return null;
                }

                return new MvFieldWrapper(MvUtil.getMvIndicators(_mvContainer), value, String.valueOf(mv));
            }

            return value;
        }

        Object innerConvert(Object value)
        {
            if (type != null)
                return type.convert(value);
            return value;
        }
    }


    private class PropertyConvertColumn extends MissingValueConvertColumn
    {
        @Nullable PropertyType pt;

        PropertyConvertColumn(String fieldName, int fromIndex, int mvIndex, boolean supportsMissingValue, @Nullable PropertyType pt, @Nullable JdbcType type)
        {
            super(fieldName, fromIndex, mvIndex, type != null ? type : pt != null ? pt.getJdbcType() : null);
            this.pt = pt;
            this.supportsMissingValue = supportsMissingValue;
        }

        @Override
        Object innerConvert(Object value)
        {
            if (null != pt)
                return pt.convert(value);
            return super.innerConvert(value);
        }
    }

    private class PropertyConvertAndTrimColumn extends PropertyConvertColumn
    {
        boolean trimRightOnly;

        PropertyConvertAndTrimColumn(String fieldName, int fromIndex, int mvIndex, boolean supportsMissingValue, @Nullable PropertyType pt, @Nullable JdbcType type, boolean trimRightOnly)
        {
            super(fieldName, fromIndex, mvIndex, supportsMissingValue, pt, type);
            this.trimRightOnly = trimRightOnly;
        }

        @Override
        Object innerConvert(Object value)
        {
            value = super.innerConvert(value);
            if (value instanceof String)
            {
                if (trimRightOnly)
                    value = StringUtils.stripEnd((String) value, "\t\r\n ");
                else
                    value = StringUtils.trim((String) value);
            }
            return value;
        }
    }

    // CONSIDER: Add JdbcType or PropertyType for array types instead of handling conversion here.
    private class MultiValueConvertColumn extends SimpleConvertColumn
    {
        private final SimpleConvertColumn _c;

        MultiValueConvertColumn(SimpleConvertColumn c)
        {
            super(c.fieldName, c.index, c.type);
            _c = c;
        }

        @Override
        protected Object convert(Object o)
        {
            Collection<Object> values = new ArrayList<>();
            if (o instanceof Object[])
            {
                for (Object o1 : (Object[])o)
                    values.add(_c.convert(o1));
            }
            else if (o instanceof Collection)
            {
                for (Object o1 : (Collection)o)
                    values.add(_c.convert(o1));
            }
            else if (o instanceof JSONArray)
            {
                // Only supports array of simple values right now.
                for (Object o1 : ((JSONArray)o).toArray())
                    values.add(_c.convert(o1));
            }
            else if (o != null)
            {
                values.add(_c.convert(o));
            }

            return values;
        }

    }

    public enum RemapMissingBehavior
    {
        /** Every incoming value must have an entry in the map. */
        Error,

        /** Incoming values without a map entry will be replaced with null. */
        Null,

        /** Incoming values without a map entry will pass through. */
        OriginalValue
    }

    protected class RemapPostConvertColumn extends SimpleConvertColumn
    {
        final SimpleConvertColumn _convertCol;
        final ColumnInfo _toCol;
        final RemapMissingBehavior _missing;
        final boolean _includeTitleColumn;

        final private RemapPostConvert _remapper;

        public RemapPostConvertColumn(final @NotNull SimpleConvertColumn convertCol, final int fromIndex, final @NotNull ColumnInfo toCol, RemapMissingBehavior missing, boolean includeTitleColumn)
        {
            super(convertCol.fieldName, convertCol.index, convertCol.type);
            _convertCol = convertCol;
            _toCol = toCol;
            _missing = missing;
            _includeTitleColumn = includeTitleColumn;
            _remapper = new RemapPostConvert(_toCol.getFkTableInfo(), _includeTitleColumn, _missing, false, true);
        }

        @Override
        protected Object convert(Object o)
        {
            try
            {
                Object value =  _convertCol.convert(o);
                ForeignKey fk = _toCol.getFk();
                // issue 40909 : allow String columns to resolve lookups by alternate key if the raw lookup fails to resolve
                if (fk != null && Objects.equals(o, value) && _toCol.getJdbcType().isText())
                {
                    if (_remapper.getPkColumn().getJdbcType().isText())
                    {
                        Object remappedValue = _remapper.mappedValue(o);
                        value = remappedValue != null ? remappedValue : value;
                    }
                }
                return value;
            }
            catch (ConversionException ex)
            {
                // don't want to attempt to resolve by target table PK because we already know there is a type mismatch
                _remapper.setIncludePkLookup(false);
                return _remapper.mappedValue(o);
            }
        }
    }

    protected class RemapColumn implements Supplier
    {
        final Supplier _inputColumn;
        final Map<?, ?> _map;
        final RemapMissingBehavior _missing;

        public RemapColumn(final int index, Map<?, ?> map, RemapMissingBehavior missing)
        {
            _inputColumn = _data.getSupplier(index);
            _map = map;
            _missing = missing;
        }

        public RemapColumn(Supplier call, Map<?, ?> map, RemapMissingBehavior missing)
        {
            _inputColumn = call;
            _map = map;
            _missing = missing;
        }

        @Override
        public Object get()
        {
            Object k = _inputColumn.get();
            if (null == k)
                return null;
            Object v = _map.get(k);
            if (null != v || _map.containsKey(k))
                return v;
            switch (_missing)
            {
                case Null:          return null;
                case OriginalValue: return k;
                case Error:
                default:            throw new ConversionException("Could not translate value: " + String.valueOf(k));
            }
        }
    }
    

    protected class NullColumn implements Supplier
    {
        @Override
        public Object get()
        {
            return null;
        }
    }

    /* use same value for all rows, set value on first usage */
    Timestamp _ts = null;

    private class TimestampColumn implements Supplier
    {
        @Override
        public Object get()
        {
            if (null == _ts)
                _ts =  new NowTimestamp(System.currentTimeMillis());
            return _ts;
        }
    }


    private class SharedTableLookupColumn implements Supplier
    {
        final int _first;
        final Integer _second;
        Map<String, Integer> _lookupStringToRowIdMap;
        Map<Object, Object> _dataspaceTableIdMap;

        SharedTableLookupColumn(int first, Integer second, Map<String, Integer> lookupStringToRowIdMap,
                                @NotNull Map<Object, Object> dataspaceTableIdMap)
        {
            _first = first;
            _second = second;
            _lookupStringToRowIdMap = lookupStringToRowIdMap;
            _dataspaceTableIdMap = dataspaceTableIdMap;
        }

        @Override
        public Object get()
        {
            Object value = _data.get(_first);

            // shared tables should be Integer->Integer
            Integer valueAsInt = null;
            if (value instanceof String)
                valueAsInt = Integer.parseInt((String)value);
            if (_dataspaceTableIdMap.containsKey(valueAsInt))
            {
                value = _dataspaceTableIdMap.get(valueAsInt);
            }
            else if (null != _second && !_lookupStringToRowIdMap.isEmpty())
            {
                String lookupString = (String)_data.get(_second);
                Integer mappedValue = _lookupStringToRowIdMap.get(lookupString);
                if (null != mappedValue)
                    value = mappedValue;
            }
            return value;
        }
    }

    public Map<String, Integer> getColumnNameMap()
    {
        if (null == _inputNameMap)
        {
            _inputNameMap = DataIteratorUtil.createColumnNameMap(_data);
        }
        return Collections.unmodifiableMap(_inputNameMap);
    }


    /*
     * CONFIGURE methods
     */

    public void selectAll()
    {
        for (int i=1 ; i<=_data.getColumnCount() ; i++)
            addColumn(i);
    }

    public void selectAll(@NotNull Set<String> skipColumns)
    {
        selectAll(skipColumns, Collections.emptyMap());
    }

    public void selectAll(@NotNull Set<String> skipColumns, @NotNull Map<String, String> translations)
    {
        Map<String, Integer> aliasColumns = new HashMap<>();
        for (int i = 1; i <= _data.getColumnCount(); i++)
        {
            ColumnInfo c = _data.getColumnInfo(i);
            String name = c.getName();
            if (skipColumns.contains(name))
                continue;

            addColumn(c, i);
            if (translations.containsKey(name))
                aliasColumns.put(translations.get(name),i);
        }

        //Append new alias columns to prevent indexing errors
        for(Map.Entry<String, Integer> alias : aliasColumns.entrySet())
            addColumn(alias.getKey(), alias.getValue());
    }


    public void removeColumn(int index)
    {
        _outputColumns.remove(index);
    }


    /** use addColumn(ColumnInfo col, Supplier call) */
    @Deprecated
    public int addColumn(ColumnInfo col, Callable call)
    {
        Supplier s = () ->
        {
            try
            {
                Object o = call.call();
                return o;
            }
            catch (Exception x)
            {
                throw new RuntimeException(x);
            }
        };
        _outputColumns.add(new Pair<>(col, s));
        return _outputColumns.size()-1;
    }

    public int addColumn(ColumnInfo col, Supplier call)
    {
        _outputColumns.add(new Pair<>(col, call));
        return _outputColumns.size()-1;
    }

    public int addColumn(int fromIndex)
    {
        ColumnInfo col = new BaseColumnInfo(_data.getColumnInfo(fromIndex));
        return addColumn(col, new PassthroughColumn(fromIndex));
    }

    public int addColumn(ColumnInfo from, int fromIndex)
    {
        ColumnInfo clone = new BaseColumnInfo(from);
        return addColumn(clone, new PassthroughColumn(fromIndex));
    }

    public int addColumn(String name, int fromIndex)
    {
        var col = new BaseColumnInfo(_data.getColumnInfo(fromIndex));
        col.setName(name);
        return addColumn(col, new PassthroughColumn(fromIndex));
    }

    public int addAliasColumn(String name, int aliasIndex)
    {
        var col = new BaseColumnInfo(_outputColumns.get(aliasIndex).getKey());
        col.setName(name);
        // don't want duplicate property ids usually
        col.setPropertyURI(null);
        return addColumn(col, new AliasColumn(name, aliasIndex));
    }

    public int addAliasColumn(String name, int aliasIndex, JdbcType toType)
    {
        var col = new BaseColumnInfo(_outputColumns.get(aliasIndex).getKey());
        col.setName(name);
        col.setJdbcType(toType);
        // don't want duplicate property ids usually
        col.setPropertyURI(null);
        return addColumn(col, new AliasColumn(name, aliasIndex, toType));
    }

    /**
     * Add convert column using the input DataIterator source column at the given
     * <code>fromIndex</code>.
     *
     * Converts the source data value at <code>fromIndex</code> using the
     * column's PropertyType or JdbcType, handles missing values, remapping lookup
     * display values to the lookup primary key, and converts multi-value foreign keys
     * into collections.
     *
     * @param col       Use this column's type to perform conversion.
     * @param fromIndex Source column index to pull data from.
     */
    public int addConvertColumn(ColumnInfo col, int fromIndex)
    {
        SimpleConvertColumn c = createConvertColumn(col, fromIndex, false);
        return addColumn(col, c);
    }

    /**
     * Add convert column using the input DataIterator source column at the given
     * <code>fromIndex</code> and the missing value column at <code>mvIndex</code>.
     *
     * Converts the source data value at <code>fromIndex</code> using the
     * column's PropertyType or JdbcType, handles missing values, remapping lookup
     * display values to the lookup primary key, and converts multi-value foreign keys
     * into collections.
     *
     * @param col       Use this column's type to perform conversion.
     * @param fromIndex Source column to create the output column from.
     * @param mvIndex   Missing value column index.
     * @param useOriginalValueOnRemapFailure When true and remapping fails, use the original value.
     *                                       When false and remapping fails, indicate an error if the column is required or null if not required.
     *                                       Used when doing "lightweight convert" prior to running trigger scripts.
     */
    public int addConvertColumn(ColumnInfo col, int fromIndex, int mvIndex, boolean useOriginalValueOnRemapFailure)
    {
        SimpleConvertColumn c = createConvertColumn(col, fromIndex, mvIndex, null, null, col.getJdbcType(), useOriginalValueOnRemapFailure, null);
        return addColumn(col, c);
    }

    /**
     * Add convert column using the input DataIterator source column at the given
     * <code>fromIndex</code> but converts values using the target <code>toType</code>
     * and <code>toFK</code>.
     *
     * Converts the source data value at <code>fromIndex</code> using the
     * column's PropertyType or JdbcType, handles missing values, remapping lookup
     * display values to the lookup primary key, and converts multi-value foreign keys
     * into collections.
     *
     * @param name      Output column name to add to this SimpleTranslator.
     * @param fromIndex Source column to create the output column from and pull data from.
     * @param toType    Convert the source data values to this type.
     * @param toFk      When <code>isAllowImportLookupByAlternateKey</code> is turned on, remap lookup values using the foreign key if there is a conversion failure.
     * @param useOriginalValueOnRemapFailure When true and remapping fails, use the original value.
     *                                       When false and remapping fails, indicate an error if the column is required or null if not required.
     *                                       Used when doing "lightweight convert" prior to running trigger scripts.
     */
    public int addConvertColumn(String name, int fromIndex, JdbcType toType, @Nullable ForeignKey toFk, boolean useOriginalValueOnRemapFailure)
    {
        var col = new BaseColumnInfo(_data.getColumnInfo(fromIndex));
        col.setName(name);
        col.setJdbcType(toType);
        if (toFk != null)
            col.setFk(toFk);

        return addConvertColumn(col, fromIndex, fromIndex, useOriginalValueOnRemapFailure);
    }

    /**
     * Add convert column using the input DataIterator source column at the given
     * <code>fromIndex</code> and the missing value column at <code>mvIndex</code>
     * but converts values using the target <code>pd</code> and <code>pt</code>.
     *
     * Converts the source data value at <code>fromIndex</code> using the
     * column's PropertyType or JdbcType, handles missing values, remapping lookup
     * display values to the lookup primary key, and converts multi-value foreign keys
     * into collections.
     *
     * @param col       Use this column's type to perform conversion.
     * @param fromIndex Source column to create the output column from and pull data from.
     * @param pd        PropertyDescriptor used for missing value enabled-ness.
     * @param pt        Convert the source data values to this type.
     * @param useOriginalValueOnRemapFailure When true and remapping fails, use the original value.
     *                                       When false and remapping fails, indicate an error if the column is required or null if not required.
     *                                       Used when doing "lightweight convert" prior to running trigger scripts.
     * @param remapMissingBehavior The behavior desired when remapping fails.  If not null, useOriginalValueOnRemapFailure is ignored.
     */
    public int addConvertColumn(@NotNull ColumnInfo col, int fromIndex, int mvIndex, @Nullable PropertyDescriptor pd, @Nullable PropertyType pt, boolean useOriginalValueOnRemapFailure, @Nullable RemapMissingBehavior remapMissingBehavior)
    {
        SimpleConvertColumn c = createConvertColumn(col, fromIndex, mvIndex, pd, pt, col.getJdbcType(), useOriginalValueOnRemapFailure, remapMissingBehavior);
        return addColumn(col, c);
    }

    public SimpleConvertColumn createConvertColumn(@NotNull ColumnInfo col, int fromIndex, boolean useOriginalValueOnRemapFailure)
    {
        return createConvertColumn(col, fromIndex, NO_MV_INDEX, null, col.getPropertyType(), col.getJdbcType(), useOriginalValueOnRemapFailure, null);
    }

    private SimpleConvertColumn createConvertColumn(@NotNull ColumnInfo col, int fromIndex, int mvIndex, @Nullable PropertyDescriptor pd, @Nullable PropertyType pt, @Nullable JdbcType type, boolean useOriginalValueOnRemapFailure, @Nullable RemapMissingBehavior remapMissingBehavior)
    {
        final String name = col.getName();

        boolean mv = null != col.getMvColumnName() || (null != pd && pd.isMvEnabled());
        boolean trimString = _context.getConfigParameterBoolean(QueryUpdateService.ConfigParameters.TrimString);
        boolean trimStringRight = _context.getConfigParameterBoolean(QueryUpdateService.ConfigParameters.TrimStringRight);

        SimpleConvertColumn c;
        if (PropertyType.STRING == pt && (trimString || trimStringRight))
            c = new PropertyConvertAndTrimColumn(name, fromIndex, mvIndex, mv, pt, type, !trimString);
        else
            c = new PropertyConvertColumn(name, fromIndex, mvIndex, mv, pt, type);

        ForeignKey fk = col.getFk();
        if (fk != null && _context.isAllowImportLookupByAlternateKey() && fk.allowImportByAlternateKey())
        {
            RemapMissingBehavior missing = remapMissingBehavior;
            if (missing == null)
            {
                if (useOriginalValueOnRemapFailure)
                    missing = RemapMissingBehavior.OriginalValue;
                else
                    missing = col.isRequired() ? RemapMissingBehavior.Error : RemapMissingBehavior.Null;
            }
            c = new RemapPostConvertColumn(c, fromIndex, col, missing, true);
        }

        boolean multiValue = fk instanceof MultiValuedForeignKey;
        if (multiValue)
        {
            // convert input into Collection of jdbcType values
            c = new MultiValueConvertColumn(c);
        }

        return c;
    }


    public int addCoaleseColumn(String name, int firstIndex, Supplier second)
    {
        var col = new BaseColumnInfo(_data.getColumnInfo(firstIndex));
        col.setName(name);
        return addColumn(col, new CoalesceColumn(firstIndex, second));
    }


    public int addCoaleseColumn(String name, int firstIndex, int secondIndex)
    {
        return addCoaleseColumn(name, firstIndex, _data.getSupplier(secondIndex));
    }


    public int addNullColumn(String name, JdbcType type)
    {
        ColumnInfo col = new BaseColumnInfo(name, type);
        return addColumn(col, new NullColumn());
    }


    public int addConstantColumn(String name, JdbcType type, Object val)
    {
        ColumnInfo col = new BaseColumnInfo(name, type);
        return addColumn(col, new ConstantColumn(val));
    }


    public int addTimestampColumn(String name)
    {
        ColumnInfo col = new BaseColumnInfo(name, JdbcType.TIMESTAMP);
        return addColumn(col, new TimestampColumn());
    }

    /**
     * Translate values from the source data iterator to those contained in the in-memory <code>map</code>.
     * @param fromIndex Source column to wrap.
     * @param map Mapping from source to value.
     * @param missing Tell me how to handle incoming values not present in the map.
     */
    public int addRemapColumn(int fromIndex, @NotNull Map<?, ?> map, RemapMissingBehavior missing)
    {
        ColumnInfo col = new BaseColumnInfo(_data.getColumnInfo(fromIndex));
        RemapColumn remap = new RemapColumn(fromIndex, map, missing);
        return addColumn(col, remap);
    }

    public int addSharedTableLookupColumn(int fromIndex, @Nullable FieldKey extraColumnFieldKey, @Nullable ForeignKey fk,
                                          @NotNull Map<Object, Object> dataspaceTableIdMap)
    {
        Integer extraColumnIndex = null;
        final Map<String, Integer> lookupStringToRowIdMap = new HashMap<>();
        if (null != extraColumnFieldKey)
        {
            assert (null != fk);
            String columnHeaderName = extraColumnFieldKey.toDisplayString();
            extraColumnIndex = getColumnNameMap().get(columnHeaderName);
            TableInfo tableInfo = fk.getLookupTableInfo();
            if (null != tableInfo)
            {
                Set<String> columnNames = new HashSet<>();
                final String lookupColumnName = extraColumnFieldKey.getName();
                final String lookupTablePkColumnName = tableInfo.getPkColumns().get(0).getName();     // Expect only 1
                columnNames.add(lookupColumnName);
                columnNames.add(lookupTablePkColumnName);
                new TableSelector(tableInfo, columnNames).forEachMap(row -> {
                    Integer rowId = (Integer)row.get(lookupTablePkColumnName);
                    String name = (String)row.get(lookupColumnName);
                    lookupStringToRowIdMap.put(name, rowId);
                });
            }
        }
        ColumnInfo col = new BaseColumnInfo(_data.getColumnInfo(fromIndex));
        return addColumn(col, new SharedTableLookupColumn(fromIndex, extraColumnIndex, lookupStringToRowIdMap, dataspaceTableIdMap));
    }

    public static DataIterator wrapBuiltInColumns(DataIterator in , DataIteratorContext context, @Nullable Container c, @NotNull User user, @NotNull TableInfo target)
    {
        SimpleTranslator t;
        if (in instanceof SimpleTranslator)
            t = (SimpleTranslator)in;
        else
        {
            t = new SimpleTranslator(in, context);
            t.selectAll();
        }
        t.addBuiltInColumns(context, c, user, target, false);
        t.addDbSequenceColumns(c, target);
        return t;
    }


    enum When
    {
        insert,
        update,
        both
    }

    public enum SpecialColumn
    {
        Container(When.insert, JdbcType.GUID),
//        Owner(When.insert, JdbcType.INTEGER),
        CreatedBy(When.insert, JdbcType.INTEGER),
        Created(When.insert, JdbcType.TIMESTAMP),
        ModifiedBy(When.both, JdbcType.INTEGER),
        Modified(When.both, JdbcType.TIMESTAMP),
        EntityId(When.insert, JdbcType.GUID);

        final When when;
        final JdbcType type;

        SpecialColumn(When when, JdbcType type)
        {
            this.when = when;
            this.type = type;
        }
    }

    public void addUniqueIdDbSequenceColumns(@Nullable Container c, @NotNull TableInfo target)
    {
        target.getColumns().stream().filter(ColumnInfo::isUniqueIdField).forEach(columnInfo -> {
            addTextSequenceColumn(columnInfo, columnInfo.getDbSequenceContainer(c), STORAGE_UNIQUE_ID_SEQUENCE_PREFIX, null, 100);
        });
    }

    public void addDbSequenceColumns(@Nullable Container c, @NotNull TableInfo target)
    {
        target
            .getColumns()
            .stream()
            .filter(columnInfo -> columnInfo.hasDbSequence() && !columnInfo.isUniqueIdField())
            .forEach(columnInfo -> {
                addSequenceColumn(columnInfo, columnInfo.getDbSequenceContainer(c), target.getDbSequenceName(columnInfo.getName()), null, 100);
            });
    }

    /**
     * Provide values for common built-in columns.  Usually we do not allow the user to specify values for these columns,
     * so matching columns in the input are ignored.
     * @param allowPassThrough indicates that columns in the input iterator should not be ignored
     */
    public void addBuiltInColumns(DataIteratorContext context, @Nullable Container c, @NotNull User user, @NotNull TableInfo target, boolean allowPassThrough)
    {
        final String containerId = null == c ? null : c.getId();
        final Integer userId = null == user ? 0 : user.getUserId();

        Supplier userCallable = new ConstantColumn(userId);
        Supplier tsCallable = new TimestampColumn();
        Supplier guidCallable = new GuidColumn();

        Map<String, Integer> inputCols = getColumnNameMap();
        Map<String, Integer> outputCols = new CaseInsensitiveHashMap<>();
        for (int i=1 ; i<_outputColumns.size() ; i++)
            outputCols.put(_outputColumns.get(i).getKey().getName(), i);

        boolean allowTargetContainers = context.getConfigParameterBoolean(QueryUpdateService.ConfigParameters.TargetMultipleContainers);

        String containerFieldKeyName = target.getContainerFieldKey() == null ? null : target.getContainerFieldKey().getName();
        Supplier containerCallable = containerFieldKeyName != null && outputCols.containsKey(containerFieldKeyName) ? new ContainerColumn(target.getUserSchema(), target, containerId, outputCols.get(containerFieldKeyName)) : new ConstantColumn(containerId);
        addBuiltinColumn(SpecialColumn.Container, allowTargetContainers, target, inputCols, outputCols, containerCallable);
        addBuiltinColumn(SpecialColumn.CreatedBy,  allowPassThrough, target, inputCols, outputCols, userCallable, context);
        addBuiltinColumn(SpecialColumn.ModifiedBy, allowPassThrough, target, inputCols, outputCols, userCallable, context);
        addBuiltinColumn(SpecialColumn.Created,    allowPassThrough, target, inputCols, outputCols, tsCallable, context);
        addBuiltinColumn(SpecialColumn.Modified,   allowPassThrough, target, inputCols, outputCols, tsCallable, context);
        addBuiltinColumn(SpecialColumn.EntityId,   allowPassThrough, target, inputCols, outputCols, guidCallable, context);
    }

    private int addBuiltinColumn(SpecialColumn e, boolean allowPassThrough, TableInfo target, Map<String,Integer> inputCols, Map<String,Integer> outputCols, Supplier c, DataIteratorContext context)
    {
        return addBuiltinColumn(e, allowPassThrough || context.getPassThroughBuiltInColumnNames().contains(e.name()), target, inputCols, outputCols, c);
    }

    private int addBuiltinColumn(SpecialColumn e, boolean allowPassThrough, TableInfo target, Map<String,Integer> inputCols, Map<String,Integer> outputCols, Supplier c)
    {
        String name = e.name();
        ColumnInfo col = target.getColumn(name);
        if (null==col)
            return 0;
        if (col.getJdbcType() != e.type && col.getJdbcType().getJavaClass() != e.type.getJavaClass())
            return 0;

        Integer indexOut = outputCols.get(name);
        Integer indexIn = inputCols.get(name);

        // not selected already
        if (null == indexOut)
        {
            if (allowPassThrough && null != indexIn)
                return addColumn(name, indexIn);
            else
            {
                _outputColumns.add(new Pair<>(new BaseColumnInfo(name, col.getJdbcType()), c));
                return _outputColumns.size()-1;
            }
        }
        // selected already
        else
        {
            if (!allowPassThrough)
                _outputColumns.set(indexOut, new Pair<>(new BaseColumnInfo(name, col.getJdbcType()), c));
            return indexOut;
        }
    }

    public int addSequenceColumn(ColumnInfo col, Container sequenceContainer, String sequenceName)
    {
        return addSequenceColumn(col, sequenceContainer, sequenceName, null, null);
    }

    public int addSequenceColumn(ColumnInfo col, Container sequenceContainer, String sequenceName, @Nullable Integer sequenceId, @Nullable Integer batchSize)
    {
        SequenceColumn seqCol = new SequenceColumn(sequenceContainer, sequenceName, sequenceId, batchSize);
        return addColumn(col, seqCol);
    }

    public int addTextSequenceColumn(ColumnInfo col, Container sequenceContainer, String sequenceName, @Nullable Integer sequenceId, @Nullable Integer batchSize)
    {
        TextIdColumn textCol = new TextIdColumn(sequenceContainer, sequenceName, sequenceId, batchSize);
        return addColumn(col, textCol);
    }

    protected static class SequenceColumn implements Supplier
    {
        // sequence settings
        private final Container seqContainer;
        private final String seqName;
        private final int seqId;
        private final int batchSize;

        // sequence state
        private DbSequence sequence;

        public SequenceColumn(Container seqContainer, String seqName, @Nullable Integer seqId, @Nullable Integer batchSize)
        {
            this.seqContainer = seqContainer;
            this.seqName = seqName;
            this.seqId = seqId == null ? 0 : seqId.intValue();
            this.batchSize = batchSize == null ? 1 : batchSize;
        }

        protected DbSequence getSequence()
        {
            if (sequence == null)
                sequence = DbSequenceManager.getPreallocatingSequence(seqContainer, seqName, seqId, batchSize);
            return sequence;
        }

        @Override
        public Object get()
        {
            DbSequence sequence = getSequence();
            return sequence.next();
        }
    }

    public static class TextIdColumn extends SequenceColumn
    {

        public TextIdColumn(Container seqContainer, String seqName, @Nullable Integer seqId, @Nullable Integer batchSize)
        {
            super(seqContainer, seqName, seqId, batchSize);
        }

        public static Object getFormattedValue(long value)
        {
            return String.format("%09d", value);
        }

        @Override
        public Object get()
        {
            DbSequence sequence = getSequence();
            return getFormattedValue(sequence.next());
        }
    }

    /**
     * @param col the column to which the PairedSequenceColumn is attached
     * @param fromIndex index of the attached column if it exists in the input
     * @param sequenceContainer sequence's container
     * @param counterDefinition counter definition
     * @param pairedIndexes indexes of the paired columns
     * @param sequencePrefix sequence name prefix
     * @param sequenceId sequenceId, if any
     * @param batchSize sequence batch size
     * @return index of PairedSequenceColumn
     */
    public int addPairedSequenceColumn(ColumnInfo col, @Nullable Integer fromIndex,
                                       Container sequenceContainer, CounterDefinition counterDefinition,
                                       List<Integer> pairedIndexes, String sequencePrefix,
                                       @Nullable Integer sequenceId, @Nullable Integer batchSize)
    {
        PairedSequenceColumn seqCol = new PairedSequenceColumn(fromIndex, sequenceContainer, sequencePrefix, counterDefinition, pairedIndexes, sequenceId, batchSize);
        return addColumn(col, seqCol);
    }

    protected class PairedSequenceColumn implements Supplier
    {
        // sequence settings
        private final Container _seqContainer;
        private final @Nullable Integer _columnIndex;
        private final List<Integer> _pairedIndexes;
        private final CounterDefinition _counterDefinition;
        private final String _sequencePrefix;
        private final int _seqId;
        private final int _batchSize;

        // sequence state
        private Map<String, DbSequence> _sequences = new HashMap<>();

        public PairedSequenceColumn(@Nullable Integer columnIndex, Container seqContainer, String sequencePrefix, CounterDefinition counterDefinition, List<Integer> pairedIndexes, @Nullable Integer seqId, @Nullable Integer batchSize)
        {
            _seqContainer = seqContainer;
            _sequencePrefix = sequencePrefix;
            _seqId = seqId == null ? 0 : seqId.intValue();
            _batchSize = batchSize == null ? 1 : batchSize;
            _pairedIndexes = pairedIndexes;
            _counterDefinition = counterDefinition;
            _columnIndex = columnIndex;
        }

        private DbSequence getSequence()
        {
            // Create the sequence name from the counter name + the paired values
            List<String> pairedValues = new ArrayList<>();
            for (Integer i : _pairedIndexes)
            {
                // We've already reported an error for missing paired column
                if (i == null)
                    return null;

                Object value = _data.get(i);
                if (null != value)
                {
                    pairedValues.add(value.toString());
                }
                else
                {
                    String name = _data.getColumnInfo(i).getName();
                    addFieldError(name, "Paired column '" + name + "' must not be null for counter '" + _counterDefinition.getCounterName() + "'");
                    return null;
                }
            }

            String seqName = _counterDefinition.getDbSequenceName(_sequencePrefix, pairedValues);
            if (!_sequences.containsKey(seqName))
                _sequences.put(seqName, DbSequenceManager.getPreallocatingSequence(_seqContainer, seqName, _seqId, _batchSize));
            return _sequences.get(seqName);
        }

        @Override
        public Object get()
        {
            // Get the current value of the counter, compare with the provided value of the bound column, throw error or get the next counter number
            DbSequence sequence = getSequence();
            if (null != sequence)
            {
                long currentValue = sequence.current();
                Object valueObj = _columnIndex != null ? _data.get(_columnIndex) : null;
                if (null != valueObj)
                {
                    int value = (int) valueObj;
                    if (value <= currentValue)
                        return value;

                    String name = _data.getColumnInfo(_columnIndex).getName();
                    addFieldError(name, "Value (" + value + ") of paired column '" + name + "' is greater than the current counter value (" + currentValue + ") for counter '" + _counterDefinition.getCounterName() + "'");
                }
                else
                {
                    return sequence.next();
                }
            }

            return null;
        }
    }

    protected class FileColumn implements Supplier<Object>
    {
        private final Container _container;
        private final String _name;
        private final int _index;
        private final String _dirName;
        private String _savedName;

        public FileColumn(Container c, String name, int idx, String dirName)
        {
            _container = c;
            _name = name;
            _dirName = dirName;
            _index = idx;
        }

        @Override
        public Object get()
        {
            if (_savedName != null)
                return _savedName;

            Object value = getInput().get(_index);
            if (value instanceof MultipartFile || value instanceof AttachmentFile)
            {
                try
                {
                    Object file = AbstractQueryUpdateService.saveFile(_container, _name, value, _dirName);
                    assert file instanceof File;
                    value = ((File)file).getPath();
                    _savedName = (String)value;
                }
                catch (QueryUpdateServiceException | ValidationException ex)
                {
                    addRowError(ex.getMessage());
                    value = null;
                }
            }
            return value;
        }
    }

    /** implementation **/

    @Override
    public int getColumnCount()
    {
        return _outputColumns.size()-1;
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        return _outputColumns.get(i).getKey();
    }

    @Override
    public boolean supportsGetExistingRecord()
    {
        return _data.supportsGetExistingRecord() && 1 <= findExistingRecordIndex();
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        _rowError = null;

        boolean hasNext = _data.next();
        if (!hasNext)
            return false;

        if (null == _row)
            _row = new Object[_outputColumns.size()];
        assert _row.length == _outputColumns.size();
        processNextInput();

        for (int i=0 ; i<_row.length ; ++i)
        {
            _row[i] = null;
            try
            {
                _row[i] = _outputColumns.get(i).getValue().get();
            }
            catch (ConversionException x)
            {
                // preferable to handle in call()
                _row[i] = addConversionException(_outputColumns.get(i).getKey().getName(), null, null, x);
            }
            catch (RuntimeException x)
            {
                throw x;
            }
            catch (Exception x)
            {
                // undone source field name???
                addFieldError(_outputColumns.get(i).getKey().getName(), x.getMessage());
            }
        }
        checkShouldCancel();
        return true;
    }

    /**
     * Allow sublcasses to process the input data before the output column are called.
     */
    protected void processNextInput()
    {
    }


    @Override
    public Object get(int i)
    {
        return _row[i];
    }


    @Override
    public Supplier<Object> getSupplier(int i)
    {
        return () -> _row[i];
    }


    // use carefully!  Mostly for implementing classes used for addColumn(Callable)
    public Object getInputColumnValue(int i)
    {
        return _data.get(i);
    }


    @Override
    public void beforeFirst()
    {
        _row = null;
        ((ScrollableDataIterator)_data).beforeFirst();
    }


    @Override
    public boolean isScrollable()
    {
        return _data instanceof ScrollableDataIterator && ((ScrollableDataIterator)_data).isScrollable();
    }


    @Override
    public boolean isConstant(int i)
    {
        Supplier c = _outputColumns.get(i).getValue();
        if (c instanceof ConstantColumn)
            return true;
        if (c instanceof PassthroughColumn)
            return _data.isConstant(((PassthroughColumn)c).index);
        if (c instanceof AliasColumn)
            return isConstant(((AliasColumn)c).index);
        if (c instanceof SimpleConvertColumn)
            return _data.isConstant(((SimpleConvertColumn)c).index);
        if (c instanceof TimestampColumn)
            return true;
        return false;
    }


    @Override
    public Object getConstantValue(int i)
    {
        Supplier c = _outputColumns.get(i).getValue();
        if (c instanceof ConstantColumn)
            return ((ConstantColumn)c).k;
        if (c instanceof PassthroughColumn)
            return _data.getConstantValue(((PassthroughColumn)c).index);
        if (c instanceof AliasColumn)
            return getConstantValue(((AliasColumn)c).index);
        if (c instanceof SimpleConvertColumn)
        {
            SimpleConvertColumn scc = (SimpleConvertColumn)c;
            return scc.convert(_data.getConstantValue(scc.index));
        }
        if (c instanceof TimestampColumn)
            return new NowTimestamp(System.currentTimeMillis());
        throw new IllegalStateException("shouldn't call this method unless isConstant()==true");
    }


    @Override
    public void close() throws IOException
    {
        _data.close();
    }


    // this is a marker interface to hint that this value may be replaced by {ts now()}
    public static class NowTimestamp extends java.sql.Timestamp
    {
        NowTimestamp(long ms)
        {
            super(ms);
        }
    }


    @Override
    public void debugLogInfo(StringBuilder sb)
    {
        super.debugLogInfo(sb);

        _outputColumns.stream()
            .forEach(p ->
            {
                sb.append("    " + p.first.getName() + " " + p.second.getClass().getSimpleName() + "\n");
            });

        if (null != _data)
            _data.debugLogInfo(sb);
    }


    /*
    * Tests
    */



    private static String[] as(String... arr)
    {
        return arr;
    }

    public static class TranslateTestCase extends Assert
    {
        StringTestIterator simpleData = new StringTestIterator
        (
            Arrays.asList("IntNotNull", "Text", "EntityId", "Int", "Lookup"),
            Arrays.asList(
                as("1", "one", GUID.makeGUID(), "", String.valueOf(LookupValues.One.ordinal())),
                as("2", "two", GUID.makeGUID(), "/N", LookupValues.Two.name()),
                as("3", "three", GUID.makeGUID(), "3", "FAIL"),
                as("4", "four", "", "4", "")
            )
        );

        enum LookupValues { One, Two, Three, Four }

        public TranslateTestCase()
        {
            simpleData.setScrollable(true);
        }


        @Test
        public void passthroughTest() throws Exception
        {
            DataIteratorContext context = new DataIteratorContext();
            simpleData.beforeFirst();
            SimpleTranslator t = new SimpleTranslator(simpleData, context);
            t.selectAll();
            assert(t.getColumnCount() == simpleData.getColumnCount());
            assertTrue(t.getColumnInfo(0).getJdbcType() == JdbcType.INTEGER);
            for (int i=1 ; i<=t.getColumnCount() ; i++)
                assertTrue(t.getColumnInfo(i).getJdbcType() == JdbcType.VARCHAR);
            for (int i=1 ; i<=4 ; i++)
            {
                assertTrue(t.next());
                assertEquals(t.get(0), i);
                assertEquals(t.get(1), String.valueOf(i));
            }
            assertFalse(t.next());
        }


        @Test
        public void testCoalesce() throws Exception
        {
            DataIteratorContext context = new DataIteratorContext();
            simpleData.beforeFirst();
            SimpleTranslator t = new SimpleTranslator(simpleData, context);
            int c = t.addCoaleseColumn("IntNotNull", 3, new GuidColumn());
            for (int i=1 ; i<=4 ; i++)
            {
                assertTrue(t.next());
                String guid = (String)t.get(c);
                assertFalse(StringUtils.isEmpty(guid));
            }
        }


        @Test
        public void convertTest() throws Exception
        {
            // w/o errors
            {
                DataIteratorContext context = new DataIteratorContext();
                simpleData.beforeFirst();
                SimpleTranslator t = new SimpleTranslator(simpleData, context);
                t.addConvertColumn("IntNotNull", 1, JdbcType.INTEGER, null, false);
                assertEquals(1, t.getColumnCount());
                assertEquals(JdbcType.INTEGER, t.getColumnInfo(0).getJdbcType());
                assertEquals(JdbcType.INTEGER, t.getColumnInfo(1).getJdbcType());
                for (int i=1 ; i<=4 ; i++)
                {
                    assertTrue(t.next());
                    assertEquals(i, t.get(0));
                    assertEquals(i, t.get(1));
                }
                assertFalse(t.next());
            }

            // w/ errors failfast==true
            {
                DataIteratorContext context = new DataIteratorContext();
                context.setFailFast(true);
                context.setVerbose(true);
                simpleData.beforeFirst();
                SimpleTranslator t = new SimpleTranslator(simpleData, context);
                t.addConvertColumn("Text", 2, JdbcType.INTEGER, null, false);
                assertEquals(t.getColumnCount(), 1);
                assertEquals(t.getColumnInfo(0).getJdbcType(), JdbcType.INTEGER);
                assertEquals(t.getColumnInfo(1).getJdbcType(), JdbcType.INTEGER);
                try
                {
                    assertFalse(t.next());
                }
                catch (BatchValidationException x)
                {
                }
                assertTrue(context._errors.hasErrors());
            }

            // w/ errors failfast==false
            {
                DataIteratorContext context = new DataIteratorContext();
                context.setFailFast(false);
                context.setVerbose(true);
                simpleData.beforeFirst();
                SimpleTranslator t = new SimpleTranslator(simpleData, context);
                t.addConvertColumn("Text", 2, JdbcType.INTEGER, null, false);
                assertEquals(t.getColumnCount(), 1);
                assertEquals(t.getColumnInfo(0).getJdbcType(), JdbcType.INTEGER);
                assertEquals(t.getColumnInfo(1).getJdbcType(), JdbcType.INTEGER);
                for (int i=1 ; i<=4 ; i++)
                {
                    assertTrue(t.next());
                    assertEquals(i, t.get(0));
                    assertNull(t.get(1));
                    assertTrue(context.getErrors().hasErrors());
                    assertEquals(i, context.getErrors().getRowErrors().size());
                }
                assertFalse(t.next());
                assertEquals(4, context.getErrors().getRowErrors().size());
            }

            // missing values
            {
            }
        }

        @Test
        public void convertRemapTest() throws Exception
        {
            // fake-o lookup table
            var core = QueryService.get().getUserSchema(TestContext.get().getUser(), JunitUtil.getTestContainer(), "core");
            var lookupTable = new EnumTableInfo<>(LookupValues.class, core, "fake enum", true);
            var fk = new AbstractForeignKey(null, null)
            {
                @Override
                public @Nullable ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
                {
                    if (displayField == null)
                        displayField = lookupTable.getTitleColumn();
                    return lookupTable.getColumn(displayField);
                }

                @Override
                public @Nullable TableInfo getLookupTableInfo() { return lookupTable; }

                @Override
                public @Nullable TableDescription getLookupTableDescription() { return null; }

                @Override
                public StringExpression getURL(ColumnInfo parent) { return null; }
            };

            // with remap with allowImportLookupByAlternateKey
            // don't throw error if remap can't be resolved
            {
                DataIteratorContext context = new DataIteratorContext();
                context.setAllowImportLookupByAlternateKey(true);
                simpleData.beforeFirst();
                SimpleTranslator t = new SimpleTranslator(simpleData, context);
                t.addConvertColumn("Lookup", 5, JdbcType.INTEGER, fk, true);
                assertEquals(1, t.getColumnCount());
                assertEquals(JdbcType.INTEGER, t.getColumnInfo(0).getJdbcType());
                assertEquals(JdbcType.INTEGER, t.getColumnInfo(1).getJdbcType());

                // first row
                assertTrue(t.next());
                assertEquals(1, t.get(0));
                assertEquals(0, t.get(1)); // convert string "0" -> rowId ordinal 0

                // second row
                assertTrue(t.next());
                assertEquals(2, t.get(0));
                assertEquals(1, t.get(1)); // convert string "Two" -> rowId ordinal 1

                // third row -- original value passed through
                assertTrue(t.next());
                assertEquals(3, t.get(0));
                assertEquals("FAIL", t.get(1)); // fails to convert

                // fourth row
                assertTrue(t.next());
                assertEquals(4, t.get(0));
                assertNull(t.get(1)); // empty string converts to null

                // no more rows
                assertFalse(t.next());
            }

        }


        @Test
        public void missingTest()
        {

        }

        @Test
        public void builtinColumns()
        {
            TableInfo t = TestSchema.getInstance().getTableInfoTestTable();
        }
    }
}
