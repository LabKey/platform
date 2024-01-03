package org.labkey.api.dataiterator;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringUtilsLabKey;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class SampleUpdateAliquotedFromDataIterator extends WrapperDataIterator
{
    public static final String ALIQUOTED_FROM_LSID_COLUMN_NAME = "AliquotedFromLSID";
    public static final String ROOT_ROW_ID_COLUMN_NAME = "RootMaterialRowId";
    static final String KEY_COLUMN_NAME = "Name";
    static final String KEY_COLUMN_LSID = "LSID";

    final CachingDataIterator _unwrapped;
    final TableInfo target;
    final int _sampleTypeId;
    final ColumnInfo pkColumn;
    final Supplier<Object> pkSupplier;
    final int _aliquotedFromColIndex;
    final int _rootMaterialRowIdColIndex;
    final boolean _useLsid;

    // prefetch of existing records
    int lastPrefetchRowNumber = -1;
    final HashMap<Integer, String> aliquotParents = new HashMap<>();
    final HashMap<Integer, Integer> aliquotRoots = new HashMap<>();

    public SampleUpdateAliquotedFromDataIterator(DataIterator in, TableInfo target, int sampleTypeId, boolean useLsid)
    {
        super(in);

        this._unwrapped = (CachingDataIterator)in;

        this.target = target;

        this._sampleTypeId = sampleTypeId;
        this._useLsid = useLsid;

        var map = DataIteratorUtil.createColumnNameMap(in);
        this._aliquotedFromColIndex = map.get(ALIQUOTED_FROM_LSID_COLUMN_NAME);
        this._rootMaterialRowIdColIndex = map.get(ROOT_ROW_ID_COLUMN_NAME);

        String keyCol = useLsid ? KEY_COLUMN_LSID : KEY_COLUMN_NAME;
        Integer index = map.get(keyCol);
        ColumnInfo col = target.getColumn(keyCol);
        if (null == index || null == col)
            throw new IllegalArgumentException("Key column not found: " + keyCol);
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
        Integer rowNumber = (Integer)_delegate.get(0);

        if (i == _aliquotedFromColIndex)
            return aliquotParents.get(rowNumber);
        else if (i == _rootMaterialRowIdColIndex)
            return aliquotRoots.get(rowNumber);

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
        Integer rowNumber = (Integer)_delegate.get(0);
        if (rowNumber <= lastPrefetchRowNumber)
            return;

        aliquotParents.clear();
        aliquotRoots.clear();

        int rowsToFetch = 50;
        Map<Integer, String> rowKeyMap = new LinkedHashMap<>();
        Map<String, Integer> keyRowMap = new LinkedHashMap<>();
        do
        {
            lastPrefetchRowNumber = (Integer) _delegate.get(0);
            Object keyObj = pkSupplier.get();

            String key = null;
            if (keyObj instanceof String)
                key = StringUtilsLabKey.unquoteString((String) keyObj);
            else if (keyObj instanceof Number)
                key = keyObj.toString();
            if (StringUtils.isEmpty(key))
                throw new IllegalArgumentException("Key value not provided on row " + lastPrefetchRowNumber);

            rowKeyMap.put(lastPrefetchRowNumber, key);
            keyRowMap.put(key, lastPrefetchRowNumber);
            aliquotParents.put(lastPrefetchRowNumber, null);
            aliquotRoots.put(lastPrefetchRowNumber, null);
        }
        while (--rowsToFetch > 0 && _delegate.next());

        String keyCol = _useLsid ? KEY_COLUMN_LSID : KEY_COLUMN_NAME;
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("MaterialSourceId"), _sampleTypeId);
        FieldKey keyField = FieldKey.fromParts(keyCol);
        filter.addCondition(keyField, rowKeyMap.values(), CompareType.IN);
        filter.addCondition(FieldKey.fromParts("Container"), target.getUserSchema().getContainer());

        Map<String, Object>[] results = new TableSelector(ExperimentService.get().getTinfoMaterial(), Sets.newCaseInsensitiveHashSet(keyCol, "aliquotedfromlsid", "rootMaterialRowId"), filter, null).getMapArray();

        for (Map<String, Object> result : results)
        {
            String key = (String) result.get(keyCol);
            Object aliquotedFromLSIDObj = result.get("aliquotedFromLSID");
            Object rootMaterialRowIdObj = result.get("rootMaterialRowId");
            Integer rowInd = keyRowMap.get(key);
            if (aliquotedFromLSIDObj != null)
                aliquotParents.put(rowInd, (String) aliquotedFromLSIDObj);
            if (rootMaterialRowIdObj != null)
                aliquotRoots.put(rowInd, (Integer) rootMaterialRowIdObj);
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
