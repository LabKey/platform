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
 *
 * Trigger scripts are invoked before insert/update/delete on many LabKey tables.
 * The Trigger is created by a TriggerFactory added to AbstractTableInfo.
 */
public interface Trigger
{
    default String getDebugName() { return getClass().getSimpleName(); }

    /**
     * True if this TriggerScript can be used in a streaming context; triggers will be called without old row values.
     */
    default boolean canStream() { return false; }

    default void batchTrigger(TableInfo table, Container c, TableInfo.TriggerType event, boolean before, BatchValidationException errors, Map<String, Object> extraContext)
            throws BatchValidationException
    {
        if (before)
            init(table, c, event, errors, extraContext);
        else
            complete(table, c, event, errors, extraContext);
    }

    default void init(TableInfo table, Container c, TableInfo.TriggerType event, BatchValidationException errors, Map<String, Object> extraContext)
    {
    }

    default void complete(TableInfo table, Container c, TableInfo.TriggerType event, BatchValidationException errors, Map<String, Object> extraContext)
    {
    }


    default void rowTrigger(TableInfo table, Container c, TableInfo.TriggerType event, boolean before, int rowNumber,
                            @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow,
                            ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
        if (before)
        {
            switch (event)
            {
                case INSERT:
                    beforeInsert(table, c, newRow, errors, extraContext);
                    break;
                case UPDATE:
                    beforeUpdate(table, c, newRow, oldRow, errors, extraContext);
                    break;
                case DELETE:
                    beforeDelete(table, c, oldRow, errors, extraContext);
                    break;
            }
        }
        else
        {
            switch (event)
            {
                case INSERT:
                    afterInsert(table, c, newRow, errors, extraContext);
                    break;
                case UPDATE:
                    afterUpdate(table, c, newRow, oldRow, errors, extraContext);
                    break;
                case DELETE:
                    afterDelete(table, c, oldRow, errors, extraContext);
                    break;
            }
        }
    }

    default void beforeInsert(TableInfo table, Container c,
                              @Nullable Map<String, Object> newRow,
                              ValidationException errors, Map<String, Object> extraContext)
    {
    }

    default void beforeUpdate(TableInfo table, Container c,
                              @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow,
                              ValidationException errors, Map<String, Object> extraContext)
    {
    }

    default void beforeDelete(TableInfo table, Container c,
                              @Nullable Map<String, Object> oldRow,
                              ValidationException errors, Map<String, Object> extraContext)
    {
    }

    default void afterInsert(TableInfo table, Container c,
                             @Nullable Map<String, Object> newRow,
                             ValidationException errors, Map<String, Object> extraContext)
    {
    }

    default void afterUpdate(TableInfo table, Container c,
                             @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow,
                             ValidationException errors, Map<String, Object> extraContext)
    {
    }

    default void afterDelete(TableInfo table, Container c,
                             @Nullable Map<String, Object> oldRow,
                             ValidationException errors, Map<String, Object> extraContext)
    {
    }

}
