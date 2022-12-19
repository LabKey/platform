package org.labkey.api.dataiterator;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

// throws error if key values are not found in the current folder
public class NoNewRecordValidationDataIterator extends RecordValidationDataIterator
{
    protected NoNewRecordValidationDataIterator(DataIterator in, DataIteratorContext context, TableInfo target, @Nullable Set<String> keys, @Nullable Map<String, Object> extraKeyValueMap, int batchSize)
    {
        super(in, context, target, keys, extraKeyValueMap, batchSize);
    }

    @Override
    public void validate(Map<Integer, Map<String, Object>> keysMap)
    {
        try
        {
            qus.verifyExistingRows(user, c, new ArrayList<>(keysMap.values()));
        }
        catch (Exception e)
        {
            _context.getErrors().addRowError(new ValidationException(e.getMessage()));
        }
    }

    public static DataIteratorBuilder createBuilder(DataIteratorBuilder dib, TableInfo target, @Nullable Set<String> keys, @Nullable Map<String,Object> extraKeyValueMap, int batchSize)
    {
        return context ->
        {
            DataIterator di = dib.getDataIterator(context);
            if (null == di)
                return null;

            QueryUpdateService.InsertOption option = context.getInsertOption();
            Container container = target.getUserSchema() == null ? null : target.getUserSchema().getContainer();
            if (option.updateOnly && container != null)
            {
                return new NoNewRecordValidationDataIterator(new CachingDataIterator(di), context, target, keys, extraKeyValueMap, batchSize);
            }
            return di;
        };
    }
}
