package org.labkey.api.etl;

import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: 2011-08-26
 * Time: 1:42 PM
 *
 * This iterator is called prepares data for the BeforeTriggerDataIterator.
 * We do a best attempt at converting to the target type, but to not fail if
 * conversion does not work.
 *
 * Also add NULL for missing columns, so that the shape does not change before/after the trigger.
 */
public class CoerceDataIterator extends SimpleTranslator
{
    public CoerceDataIterator(DataIterator source, BatchValidationException errors, TableInfo target)
    {
        super(source, errors);

        init(target);
    }

    void init(TableInfo target)
    {
        Set<String> seen = new CaseInsensitiveHashSet();
        DataIterator di = getInput();
        int count = di.getColumnCount();
        for (int i=1 ; i<=count ; i++)
        {
            ColumnInfo from = di.getColumnInfo(i);
            ColumnInfo to = target.getColumn(from.getName());
            // TODO CONSIDER : do we rename columns here so that they are consistent in the trigger script?
            if (null != to)
            {
                seen.add(to.getName());
                addConvertColumn(to.getName(), i, to.getJdbcType(), false);
            }
            else
            {
                addColumn(i);
            }
        }
        // add null column for all insertable not seen columns
        for (ColumnInfo to : target.getColumns())
        {
            if (seen.contains(target.getName()))
                continue;
            addNullColumn(to.getName(), to.getJdbcType());
        }
    }

    @Override
    protected Object addConversionException(String fieldName, Object value, JdbcType target, Exception x)
    {
        return value;
    }
}
