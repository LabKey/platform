package org.labkey.api.dataiterator;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.collections.IntHashMap;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.labkey.api.exp.query.ExpMaterialTable.Column.*;
import static org.labkey.api.util.IntegerUtils.asInteger;

public class SampleUpdateAddColumnsDataIterator extends WrapperDataIterator
{
    public static final String CURRENT_SAMPLE_STATUS_COLUMN_NAME = "_CurrentSampleState_";

    final CachingDataIterator _unwrapped;
    final TableInfo target;
    final long _sampleTypeId;
    final ColumnInfo pkColumn;
    final Supplier<Object> pkSupplier;
    final int _aliquotedFromColIndex;
    final int _rootMaterialRowIdColIndex;
    final int _currentSampleStateColIndex;

    // prefetch of existing records
    int lastPrefetchRowNumber = -1;
    final IntHashMap<String> aliquotParents = new IntHashMap<>();
    final IntHashMap<Integer> aliquotRoots = new IntHashMap<>();
    final IntHashMap<Integer> sampleState = new IntHashMap<>();

    public SampleUpdateAddColumnsDataIterator(DataIterator in, TableInfo target, long sampleTypeId, String keyColumnName)
    {
        super(in);
        this._unwrapped = (CachingDataIterator)in;
        this.target = target;
        this._sampleTypeId = sampleTypeId;

        var map = DataIteratorUtil.createColumnNameMap(in);
        this._aliquotedFromColIndex = map.get(AliquotedFromLSID.name());
        this._rootMaterialRowIdColIndex = map.get(RootMaterialRowId.name());
        this._currentSampleStateColIndex = map.get(CURRENT_SAMPLE_STATUS_COLUMN_NAME);

        Integer index = map.get(keyColumnName);
        ColumnInfo col = target.getColumn(keyColumnName);
        if (null == index || null == col)
            throw new IllegalArgumentException("Key column not found: " + keyColumnName);
        pkSupplier = in.getSupplier(index);
        pkColumn = col;
    }

    @Override
    public Supplier<Object> getSupplier(int i)
    {
        if (i != _aliquotedFromColIndex && i != _rootMaterialRowIdColIndex)
            return _delegate.getSupplier(i);
        return () -> get(i);
    }

    @Override
    public Object get(int i)
    {
        Integer rowNumber = asInteger(_delegate.get(0));

        if (i == _aliquotedFromColIndex)
            return aliquotParents.get(rowNumber);
        else if (i == _rootMaterialRowIdColIndex)
            return aliquotRoots.get(rowNumber);
        else if (i == _currentSampleStateColIndex)
            return sampleState.get(rowNumber);

        return _delegate.get(i);
    }

    @Override
    public boolean isConstant(int i)
    {
        if (i != _aliquotedFromColIndex && i != _rootMaterialRowIdColIndex)
            return _delegate.isConstant(i);
        return false;
    }

    @Override
    public Object getConstantValue(int i)
    {
        if (i != _aliquotedFromColIndex && i != _rootMaterialRowIdColIndex)
            return _delegate.getConstantValue(i);
        return null;
    }

    protected void prefetchExisting() throws BatchValidationException
    {
        Integer rowNumber = asInteger(_delegate.get(0));
        if (rowNumber <= lastPrefetchRowNumber)
            return;

        aliquotParents.clear();
        aliquotRoots.clear();
        sampleState.clear();

        int rowsToFetch = 50;
        String keyFieldName = pkColumn.getName();
        boolean numericKey = pkColumn.isNumericType();
        JdbcType jdbcType = pkColumn.getJdbcType();
        Map<Integer, Object> rowKeyMap = new LinkedHashMap<>();
        Map<Object, Integer> keyRowMap = new LinkedHashMap<>();
        do
        {
            lastPrefetchRowNumber = asInteger(_delegate.get(0));
            Object keyObj = pkSupplier.get();
            Object key = jdbcType.convert(keyObj);

            if (numericKey)
            {
                if (null == key)
                    throw new IllegalArgumentException(keyFieldName + " value not provided on row " + lastPrefetchRowNumber);
            }
            else if (StringUtils.isEmpty((String) key))
                throw new IllegalArgumentException(keyFieldName + " value not provided on row " + lastPrefetchRowNumber);

            rowKeyMap.put(lastPrefetchRowNumber, key);
            keyRowMap.put(key, lastPrefetchRowNumber);
            aliquotParents.put(lastPrefetchRowNumber, null);
            aliquotRoots.put(lastPrefetchRowNumber, null);
            sampleState.put(lastPrefetchRowNumber, null);
        }
        while (--rowsToFetch > 0 && _delegate.next());

        SimpleFilter filter = new SimpleFilter(MaterialSourceId.fieldKey(), _sampleTypeId);
        filter.addCondition(pkColumn.getFieldKey(), rowKeyMap.values(), CompareType.IN);
        filter.addCondition(FieldKey.fromParts("Container"), target.getUserSchema().getContainer());

        Set<String> columns = Sets.newCaseInsensitiveHashSet(keyFieldName, AliquotedFromLSID.name(), RootMaterialRowId.name(), SampleState.name());
        Map<String, Object>[] results = new TableSelector(ExperimentService.get().getTinfoMaterial(), columns, filter, null).getMapArray();

        for (Map<String, Object> result : results)
        {
            Object key = result.get(keyFieldName);
            Object aliquotedFromLSIDObj = result.get(AliquotedFromLSID.name());
            Object rootMaterialRowIdObj = result.get(RootMaterialRowId.name());
            Object sampleStateObj = result.get(SampleState.name());
            Integer rowInd = keyRowMap.get(key);
            if (aliquotedFromLSIDObj != null)
                aliquotParents.put(rowInd, (String) aliquotedFromLSIDObj);
            if (rootMaterialRowIdObj != null)
                aliquotRoots.put(rowInd, (Integer) rootMaterialRowIdObj);
            if (sampleStateObj != null)
                sampleState.put(rowInd, (Integer) sampleStateObj);
        }

        // backup to where we started so caller can iterate through them one at a time
        _unwrapped.reset(); // unwrapped _delegate
        _delegate.next();
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        // NOTE: we have to call mark() before we call next() if we want the 'next' row to be cached
        _unwrapped.mark();  // unwrapped _delegate
        boolean ret = super.next();
        if (ret)
            prefetchExisting();
        return ret;
    }
}
