package org.labkey.api.dataiterator;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.IntHashMap;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;

import java.util.HashSet;
import java.util.List;
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
public class DataClassUpdateAddColumnsDataIterator extends AbstractPrefetchingDataIterator
{
    private final Container _targetContainer;
    private final long _dataClassId;
    final int _lsidColIndex;

    // prefetch of existing records
    final IntHashMap<String> lsids = new IntHashMap<>();

    public DataClassUpdateAddColumnsDataIterator(CachingDataIterator in, @NotNull DataIteratorContext context, TableInfo target, Container container, long dataClassId, String keyColumnName)
    {
        super(in, context, target, keyColumnName);
        _targetContainer = container;
        _dataClassId = dataClassId;

        var map = DataIteratorUtil.createColumnNameMap(in);
        Integer lsidIdx = map.get(ExpDataTable.Column.LSID.name());
        if (lsidIdx == null)
            throw new IllegalStateException("LSID column not found in input.");
        this._lsidColIndex = lsidIdx;
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

    @Override
    protected void prefetchExisting() throws BatchValidationException
    {
        Integer rowNumber = asInteger(_delegate.get(0));
        if (rowNumber <= lastPrefetchRowNumber)
            return;

        lsids.clear();

        BatchResult batch = buildBatch();
        Map<Integer, Object> rowKeyMap = batch.rowKeyMap();
        Map<Object, List<Integer>> keyRowMap = batch.keyRowMap();
        Set<Object> notFoundKeys = new HashSet<>(keyRowMap.keySet());

        for (Integer rowInd : rowKeyMap.keySet())
            lsids.put(rowInd, null);

        String keyFieldName = pkColumn.getName();
        SimpleFilter filter = new SimpleFilter(ClassId.fieldKey(), _dataClassId);
        filter.addCondition(pkColumn.getFieldKey(), rowKeyMap.values(), CompareType.IN);
        filter.addCondition(FieldKey.fromParts("Container"), _targetContainer);

        Set<String> columns = Sets.newCaseInsensitiveHashSet(keyFieldName, LSID.name());
        Map<String, Object>[] results = new TableSelector(ExperimentService.get().getTinfoData(), columns, filter, null).getMapArray();

        for (Map<String, Object> result : results)
        {
            Object key = result.get(keyFieldName);
            Object lsidObj = result.get(LSID.name());

            if (lsidObj != null)
            {
                for (Integer rowInd : keyRowMap.get(key))
                    lsids.put(rowInd, (String) lsidObj);
                notFoundKeys.remove(key);
            }
        }

        if (!notFoundKeys.isEmpty())
            _context.getErrors().addRowError(new ValidationException("Data does not exist in " + _targetContainer.getName() + ": " + notFoundKeys + "."));

        resetAfterBatch();
    }
}
