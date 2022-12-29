package org.labkey.api.dataiterator;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;

import java.util.Map;
import java.util.Set;

// throws error if key values found in related folders
public class CrossFolderRecordDataIterator extends RecordValidationDataIterator
{

    protected CrossFolderRecordDataIterator(DataIterator in, DataIteratorContext context, TableInfo target, @Nullable Set<String> keys, @Nullable Map<String, Object> extraKeyValueMap, int batchSize)
    {
        super(in, context, target, keys, extraKeyValueMap, batchSize);
    }

    @Override
    public void validate(Map<Integer, Map<String, Object>> keysMap)
    {
        if (qus.hasExistingRowsInOtherContainers(c, keysMap))
            _context.getErrors().addRowError(new ValidationException("Cannot update data that don't belong to the current container."));
    }

    public static DataIteratorBuilder createBuilder(DataIteratorBuilder dib, TableInfo target, @Nullable Set<String> keys, @Nullable Map<String,Object> extraKeyValueMap, int batchSize)
    {
        return context ->
        {
            DataIterator di = dib.getDataIterator(context);
            if (null == di)
                return null;

            if (di.supportsGetExistingRecord()) // use ExistingRecordDataIterator to verify data folder
                return di;

            QueryUpdateService.InsertOption option = context.getInsertOption();
            Container container = target.getUserSchema() == null ? null : target.getUserSchema().getContainer();
            if ((option.mergeRows || option.updateOnly)&& container != null && container.isProductProjectsEnabled())
            {
                return new CrossFolderRecordDataIterator(new CachingDataIterator(di), context, target, keys, extraKeyValueMap, batchSize);
            }
            return di;
        };
    }

}
