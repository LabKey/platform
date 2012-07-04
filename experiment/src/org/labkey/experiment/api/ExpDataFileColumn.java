package org.labkey.experiment.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;

import java.io.IOException;
import java.io.Writer;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 7/3/12
 * Time: 8:52 AM
 */
abstract class ExpDataFileColumn extends DataColumn
{
    private static final String DATA_OBJECT_KEY = DataLinkColumn.class + "-DataObject";

    public ExpDataFileColumn(ColumnInfo col)
    {
        super(col);
    }

    protected ExpData getData(RenderContext ctx)
    {
        Integer rowIdObject = ctx.get(getColumnInfo().getFieldKey(), Integer.class);
        ExpData data = null;
        if (rowIdObject != null)
        {
            int rowId = rowIdObject.intValue();
            // Check if another column has already grabbed the value
            data = (ExpData)ctx.get(DATA_OBJECT_KEY);
            if (data == null || data.getRowId() != rowId)
            {
                data = ExperimentService.get().getExpData(rowId);
                // Cache it for other columns to use
                ctx.put(DATA_OBJECT_KEY, data);
            }
        }
        return data;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        ExpData data = getData(ctx);

        if (data != null)
        {
            renderData(out, data);
        }
    }

    protected abstract void renderData(Writer out, ExpData data) throws IOException;

    @Override
    public Object getJsonValue(RenderContext ctx)
    {
        ExpData data = getData(ctx);
        if (data == null)
            return null;
        else
            return getJsonValue(data);
    }

    protected abstract Object getJsonValue(ExpData data);

    @Override
    public boolean isFilterable()
    {
        return false;
    }

    @Override
    public boolean isSortable()
    {
        return false;
    }

}
