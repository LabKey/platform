package org.labkey.api.dataiterator;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.IntHashMap;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.labkey.api.exp.query.ExpMaterialTable.Column.*;
import static org.labkey.api.util.IntegerUtils.asInteger;

public class SampleUpdateAddColumnsDataIterator extends AbstractPrefetchingDataIterator
{
    public static final String CURRENT_SAMPLE_STATUS_COLUMN_NAME = "_CurrentSampleState_";

    final long _sampleTypeId;
    final int _aliquotedFromColIndex;
    final int _rootMaterialRowIdColIndex;
    final int _currentSampleStateColIndex;

    // prefetch of existing records
    final IntHashMap<String> aliquotParents = new IntHashMap<>();
    final IntHashMap<Integer> aliquotRoots = new IntHashMap<>();
    final IntHashMap<Integer> sampleState = new IntHashMap<>();

    public SampleUpdateAddColumnsDataIterator(CachingDataIterator in, @NotNull DataIteratorContext context, TableInfo target, long sampleTypeId, String keyColumnName)
    {
        super(in, context, target, keyColumnName);
        this._sampleTypeId = sampleTypeId;

        var map = DataIteratorUtil.createColumnNameMap(in);
        this._aliquotedFromColIndex = map.get(AliquotedFromLSID.name());
        this._rootMaterialRowIdColIndex = map.get(RootMaterialRowId.name());
        this._currentSampleStateColIndex = map.get(CURRENT_SAMPLE_STATUS_COLUMN_NAME);
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

    @Override
    protected void prefetchExisting() throws BatchValidationException
    {
        Integer rowNumber = asInteger(_delegate.get(0));
        if (rowNumber <= lastPrefetchRowNumber)
            return;

        aliquotParents.clear();
        aliquotRoots.clear();
        sampleState.clear();

        BatchResult batch = buildBatch();
        Map<Integer, Object> rowKeyMap = batch.rowKeyMap();
        Map<Object, List<Integer>> keyRowMap = batch.keyRowMap();

        for (Integer rowInd : rowKeyMap.keySet())
        {
            aliquotParents.put(rowInd, null);
            aliquotRoots.put(rowInd, null);
            sampleState.put(rowInd, null);
        }

        String keyFieldName = pkColumn.getName();
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
            for (Integer rowInd : keyRowMap.get(key))
            {
                if (aliquotedFromLSIDObj != null)
                    aliquotParents.put(rowInd, (String) aliquotedFromLSIDObj);
                if (rootMaterialRowIdObj != null)
                    aliquotRoots.put(rowInd, (Integer) rootMaterialRowIdObj);
                if (sampleStateObj != null)
                    sampleState.put(rowInd, (Integer) sampleStateObj);
            }
        }

        resetAfterBatch();
    }
}
