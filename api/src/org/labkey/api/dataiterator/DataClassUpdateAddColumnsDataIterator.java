package org.labkey.api.dataiterator;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.IntHashMap;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.labkey.api.exp.query.ExpDataTable.Column.LSID;
import static org.labkey.api.exp.query.ExpDataTable.Column.ClassId;
import static org.labkey.api.util.IntegerUtils.asInteger;

/**
 * DataIterator that adds the LSID column for DataClass update operations.
 * Queries the LSID from exp.data based on the provided key (rowId or name) and dataClassId.
 * The LSID is needed downstream for attachment handling.
 */
public class DataClassUpdateAddColumnsDataIterator extends WrapperDataIterator
{
    private final Container _targetContainer;
    private final TableInfo _tableInfo;
    final CachingDataIterator _unwrapped;

    private final long _dataClassId;
    final int _lsidColIndex;
    final ColumnInfo pkColumn;
    final Supplier<Object> pkSupplier;

    int lastPrefetchRowNumber = -1;
    final IntHashMap<String> lsids = new IntHashMap<>();
    final DataIteratorContext _context;

    public DataClassUpdateAddColumnsDataIterator(DataIterator in, @NotNull DataIteratorContext context, TableInfo target, Container container, long dataClassId, String keyColumnName)
    {
        super(in);
        this._unwrapped = (CachingDataIterator)in;
        _context = context;
        _tableInfo = target;
        _targetContainer = container;
        _dataClassId = dataClassId;

        var map = DataIteratorUtil.createColumnNameMap(in);

        this._lsidColIndex = map.get(ExpDataTable.Column.LSID.name());

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
        if (i != _lsidColIndex)
            return _delegate.getSupplier(i);
        return () -> get(i);
    }

    @Override
    public Object get(int i)
    {
        Integer rowNumber = asInteger(_delegate.get(0));

        if (i == _lsidColIndex)
            return lsids.get(rowNumber);

        return _delegate.get(i);
    }

    @Override
    public boolean isConstant(int i)
    {
        if (i != _lsidColIndex)
            return _delegate.isConstant(i);
        return false;
    }

    @Override
    public Object getConstantValue(int i)
    {
        if (i != _lsidColIndex)
            return _delegate.getConstantValue(i);
        return null;
    }

    protected void prefetchExisting() throws BatchValidationException
    {
        Integer rowNumber = asInteger(_delegate.get(0));
        if (rowNumber <= lastPrefetchRowNumber)
            return;

        lsids.clear();

        int rowsToFetch = 50;
        String keyFieldName = pkColumn.getName();
        boolean numericKey = pkColumn.isNumericType();
        JdbcType jdbcType = pkColumn.getJdbcType();
        Map<Integer, Object> rowKeyMap = new LinkedHashMap<>();
        Map<Object, Set<Integer>> keyRowMap = new LinkedHashMap<>();
        Set<Object> notFoundKeys = new HashSet<>();

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
            notFoundKeys.add(key);
            // if keyRowMap doesn't contain key, add new set, then add row number to set for this key
            if (!keyRowMap.containsKey(key))
                keyRowMap.put(key, new HashSet<>());
            keyRowMap.get(key).add(lastPrefetchRowNumber);
            lsids.put(lastPrefetchRowNumber, null);
        }
        while (--rowsToFetch > 0 && _delegate.next());

        SimpleFilter filter = new SimpleFilter(ClassId.fieldKey(), _dataClassId);
        filter.addCondition(pkColumn.getFieldKey(), rowKeyMap.values(), CompareType.IN);
        filter.addCondition(FieldKey.fromParts("Container"), _targetContainer);

        Set<String> columns = Sets.newCaseInsensitiveHashSet(keyFieldName, LSID.name());
        Map<String, Object>[] results = new TableSelector(ExperimentService.get().getTinfoData(), columns, filter, null).getMapArray();

        for (Map<String, Object> result : results)
        {
            Object key = result.get(keyFieldName);
            Object lsidObj = result.get(LSID.name());

            Set<Integer> rowInds = keyRowMap.get(key);
            if (lsidObj != null)
            {
                for (Integer rowInd : rowInds)
                    lsids.put(rowInd, (String) lsidObj);
                notFoundKeys.remove(key);
            }
        }

        if (!notFoundKeys.isEmpty())
            _context.getErrors().addRowError(new ValidationException("Data not found for " + notFoundKeys));

        // backup to where we started so caller can iterate through them one at a time
        _unwrapped.reset(); // unwrapped _delegate
        _delegate.next();
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        if (_context.getErrors().hasErrors())
            return false;

        // NOTE: we have to call mark() before we call next() if we want the 'next' row to be cached
        _unwrapped.mark();  // unwrapped _delegate
        boolean ret = super.next();
        if (ret)
            prefetchExisting();
        return ret;
    }
}