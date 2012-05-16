package org.labkey.api.study.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;

import java.io.IOException;

/**
* User: jeckels
* Date: May 15, 2012
*/
public abstract class DataInputColumn extends PublishResultsQueryView.InputColumn
{
    protected ColumnInfo _requiredColumn;

    public DataInputColumn(String caption, String formElementName, boolean editable, String completionBase, PublishResultsQueryView.ResolverHelper resolverHelper,
                           ColumnInfo requiredColumn)
    {
        super(caption, editable, formElementName, completionBase, resolverHelper);
        _requiredColumn = requiredColumn;
    }

    protected abstract Object calculateValue(RenderContext ctx) throws IOException;

    public Object getValue(RenderContext ctx)
    {
        try
        {
            return calculateValue(ctx);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
//            if (_requiredColumn == null)
//                return null;
//            return ctx.getRow().get(_requiredColumn.getAlias());
    }
}
