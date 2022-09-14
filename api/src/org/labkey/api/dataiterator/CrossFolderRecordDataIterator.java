package org.labkey.api.dataiterator;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

// throws error if key values found in related folders
public class CrossFolderRecordDataIterator extends WrapperDataIterator
{
    private final DataIteratorContext _context;
    final QueryUpdateService qus;
    final User user;
    final Container c;

    final CachingDataIterator _unwrapped;
    final ArrayList<ColumnInfo> pkColumns = new ArrayList<>();
    final ArrayList<Supplier<Object>> pkSuppliers = new ArrayList<>();

    final Map<String,Object> _extraKeyValueMap;

    int lastPrefetchRowNumber = -1;

    protected CrossFolderRecordDataIterator(DataIterator in, DataIteratorContext context, TableInfo target, @Nullable Set<String> keys, @Nullable Map<String,Object> extraKeyValueMap)
    {
        super(in);
        _context = context;
        qus = target.getUpdateService();
        user = target.getUserSchema().getUser();
        c = target.getUserSchema().getContainer();
        _extraKeyValueMap = extraKeyValueMap;

        _unwrapped = (CachingDataIterator)in;

        var map = DataIteratorUtil.createColumnNameMap(in);
        Collection<String> keyNames = null==keys ? target.getPkColumnNames() : keys;
        for (String name : keyNames)
        {
            Integer index = map.get(name);
            ColumnInfo col = target.getColumn(name);
            if (null == index || null == col)
            {
                pkSuppliers.clear();
                pkColumns.clear();
                throw new IllegalStateException("Key column not found: " + name);
            }
            pkSuppliers.add(in.getSupplier(index));
            pkColumns.add(col);
        }
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        _unwrapped.mark();  // unwrapped _delegate
        boolean ret = super.next();
        if (ret && !pkColumns.isEmpty())
            prefetchExisting();
        return ret;
    }

    protected void prefetchExisting() throws BatchValidationException
    {
        Integer rowNumber = (Integer)_delegate.get(0);
        if (rowNumber <= lastPrefetchRowNumber)
            return;

        int rowsToFetch = 200;
        Map<Integer, Map<String,Object>> keysMap = new LinkedHashMap<>();
        do
        {
            lastPrefetchRowNumber = (Integer) _delegate.get(0);
            Map<String,Object> keyMap = CaseInsensitiveHashMap.of();
            for (int p=0 ; p<pkColumns.size() ; p++)
                keyMap.put(pkColumns.get(p).getColumnName(), pkSuppliers.get(p).get());
            if (_extraKeyValueMap != null)
                keyMap.putAll(_extraKeyValueMap);
            keysMap.put(lastPrefetchRowNumber, keyMap);
        }
        while (--rowsToFetch > 0 && _delegate.next());

        if (qus.hasExistingRowsInOtherContainers(c, keysMap))
            _context.getErrors().addRowError(new ValidationException("Cannot update data that don't belong to the current container."));

        // backup to where we started so caller can iterate through them one at a time
        _unwrapped.reset(); // unwrapped _delegate
        _delegate.next();
    }

    public static DataIteratorBuilder createBuilder(DataIteratorBuilder dib, TableInfo target, @Nullable Set<String> keys, @Nullable Map<String,Object> extraKeyValueMap)
    {
        return context ->
        {
            DataIterator di = dib.getDataIterator(context);
            if (null == di)
                return null;

            QueryUpdateService.InsertOption option = context.getInsertOption();
            if (option.mergeRows && QueryService.get().isProductProjectsEnabled(target.getUserSchema().getContainer()))
            {
                return new CrossFolderRecordDataIterator(new CachingDataIterator(di), context, target, keys, extraKeyValueMap);
            }
            return di;
        };
    }

}
