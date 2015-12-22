package org.labkey.api.data.triggers;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;

import java.util.Map;

/**
 * User: kevink
 * Date: 12/21/15
 */
public interface TriggerScript
{
    default String getDebugName() { return getClass().getSimpleName(); }

    /**
     * True if this TriggerScript can be used in a streaming context; triggers will be called without old row values.
     */
    default boolean canStream() { return false; }

    default Boolean batchTrigger(TableInfo table, Container c, TableInfo.TriggerType event, boolean before, BatchValidationException errors, Map<String, Object> extraContext)
            throws BatchValidationException
    {
        if (before)
            return init(table, c, event, errors, extraContext);
        else
            return complete(table, c, event, errors, extraContext);
    }

    Boolean init(TableInfo table, Container c, TableInfo.TriggerType event, BatchValidationException errors, Map<String, Object> extraContext);

    Boolean complete(TableInfo table, Container c, TableInfo.TriggerType event, BatchValidationException errors, Map<String, Object> extraContext);


    default Boolean rowTrigger(TableInfo table, Container c, TableInfo.TriggerType event, boolean before, int rowNumber,
                               @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow,
                               ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
        Boolean success = null;
        if (before)
        {
            switch (event)
            {
                case INSERT:
                    success = beforeInsert(table, c, newRow, errors, extraContext);
                    break;
                case UPDATE:
                    success = beforeUpdate(table, c, newRow, oldRow, errors, extraContext);
                    break;
                case DELETE:
                    success = beforeDelete(table, c, oldRow, errors, extraContext);
                    break;
            }
        }
        else
        {
            switch (event)
            {
                case INSERT:
                    success = afterInsert(table, c, newRow, errors, extraContext);
                    break;
                case UPDATE:
                    success = afterUpdate(table, c, newRow, oldRow, errors, extraContext);
                    break;
                case DELETE:
                    success = afterDelete(table, c, oldRow, errors, extraContext);
                    break;
            }
        }

        return success;
    }

    Boolean beforeInsert(TableInfo table, Container c,
                         @Nullable Map<String, Object> newRow,
                         ValidationException errors, Map<String, Object> extraContext);

    Boolean beforeUpdate(TableInfo table, Container c,
                         @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow,
                         ValidationException errors, Map<String, Object> extraContext);

    Boolean beforeDelete(TableInfo table, Container c,
                         @Nullable Map<String, Object> oldRow,
                         ValidationException errors, Map<String, Object> extraContext);

    Boolean afterInsert(TableInfo table, Container c,
                        @Nullable Map<String, Object> newRow,
                        ValidationException errors, Map<String, Object> extraContext);

    Boolean afterUpdate(TableInfo table, Container c,
                        @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow,
                        ValidationException errors, Map<String, Object> extraContext);

    Boolean afterDelete(TableInfo table, Container c,
                        @Nullable Map<String, Object> oldRow,
                        ValidationException errors, Map<String, Object> extraContext);


}
