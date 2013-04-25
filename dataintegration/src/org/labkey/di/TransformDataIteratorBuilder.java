package org.labkey.di;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.SimpleTranslator;
import static org.labkey.di.DataIntegrationDbSchema.Columns.*;

/**
 * Created with IntelliJ IDEA.
 * User: matthew
 * Date: 4/24/13
 * Time: 1:23 PM
 */
public class TransformDataIteratorBuilder implements DataIteratorBuilder
{
    final int _txTransformRunId;
    final DataIteratorBuilder _input;

    public TransformDataIteratorBuilder(int transformRunId, DataIteratorBuilder input)
    {
        _txTransformRunId = transformRunId;
        _input = input;
    }

    @Override
    public DataIterator getDataIterator(DataIteratorContext context)
    {
        DataIterator in = _input.getDataIterator(context);
        SimpleTranslator out = new SimpleTranslator(in, context);
        for (int i=1 ; i<=in.getColumnCount() ; i++)
        {
            ColumnInfo c = in.getColumnInfo(i);
            if (c.getName().startsWith("_tx"))
                continue;
            out.addColumn(i);
        }
        out.addConstantColumn(TransformRunId.getColumnName(), JdbcType.INTEGER, _txTransformRunId);
        return out;
    }
}